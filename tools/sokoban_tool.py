"""
推箱子关卡工具：求解 / 校验 / 生成。

用法:
  python sokoban_tool.py gen  <输出文件> [数量] [种子]   生成关卡候选
  python sokoban_tool.py build <手工关卡> <生成关卡> <assets/levels.txt>
                                                       校验全部关卡、计算最优步数并按难度合并

关卡格式为标准 XSB:
  #  墙   空格 地板   $ 箱子   . 目标   * 目标上的箱子   @ 玩家   + 目标上的玩家
"""
import collections
import heapq
import random
import sys

DIR_CHARS = "udlr"


class Level:
    def __init__(self, rows, title=""):
        rows = [r.rstrip("\n") for r in rows]
        self.title = title
        # 四周补一圈，越界一律视为墙
        self.w = max(len(r) for r in rows) + 2
        self.h = len(rows) + 2
        w = self.w
        self.dirs = (-w, w, -1, 1)
        self.walls = set()
        self.goals = set()
        self.boxes = set()
        self.player = None
        for y, r in enumerate(rows):
            for x, c in enumerate(r):
                i = (y + 1) * w + (x + 1)
                if c == "#":
                    self.walls.add(i)
                if c in ".+*":
                    self.goals.add(i)
                if c in "$*":
                    self.boxes.add(i)
                if c in "@+":
                    self.player = i
        if self.player is None:
            raise ValueError("没有玩家")
        # 玩家可达区域（箱子视为可穿过）即为地板
        self.floor = set()
        q = [self.player]
        self.floor.add(self.player)
        while q:
            c = q.pop()
            for d in self.dirs:
                n = c + d
                x, y = n % w, n // w
                if x <= 0 or y <= 0 or x >= w - 1 or y >= self.h - 1:
                    if n not in self.walls:
                        raise ValueError("地图未封闭")
                    continue
                if n not in self.walls and n not in self.floor:
                    self.floor.add(n)
                    q.append(n)
        if not self.boxes <= self.floor or not self.goals <= self.floor:
            raise ValueError("箱子或目标在可达区域之外")
        if len(self.boxes) != len(self.goals):
            raise ValueError("箱子数 %d 与目标数 %d 不一致" % (len(self.boxes), len(self.goals)))
        self.live = live_squares(self.floor, self.goals, self.dirs)


def live_squares(floor, goals, dirs):
    """能被拉回目标的格子；箱子推到其他格子必死。"""
    live = set(goals)
    q = list(goals)
    while q:
        b = q.pop()
        for d in dirs:
            c = b - d       # 箱子被拉到的位置
            p = b - 2 * d   # 拉时玩家站的位置
            if c in floor and p in floor and c not in live:
                live.add(c)
                q.append(c)
    return live


def frozen(t, bset, walls, goals, w):
    """简单 2x2 死锁：推动后 t 所在的某个 2x2 方块全是墙/箱子，且其中有不在目标上的箱子。"""
    for ox, oy in ((0, 0), (-1, 0), (0, -1), (-1, -1)):
        tl = t + ox + oy * w
        cells = (tl, tl + 1, tl + w, tl + w + 1)
        if all(c in walls or c in bset for c in cells):
            if any(c in bset and c not in goals for c in cells):
                return True
    return False


def solve(lv, max_states=3_000_000):
    """Dijkstra 求最少步数解（推箱也计一步）。返回 (步数, 推数, LURD 解) 或 None。"""
    w = lv.w
    dirs = lv.dirs
    floor, goals, live, walls = lv.floor, lv.goals, lv.live, lv.walls
    start = (lv.player, tuple(sorted(lv.boxes)))
    best = {start: 0}
    parent = {start: None}
    pq = [(0, 0, start)]
    tick = 0
    goal_set = frozenset(goals)
    while pq:
        d, _, s = heapq.heappop(pq)
        if best[s] < d:
            continue
        p, boxes = s
        bset = set(boxes)
        if bset == goal_set:
            return d, reconstruct(lv, parent, s)
        if len(best) > max_states:
            return None
        pdist = {p: 0}
        q = collections.deque([p])
        while q:
            c = q.popleft()
            for dd in dirs:
                n = c + dd
                if n in floor and n not in bset and n not in pdist:
                    pdist[n] = pdist[c] + 1
                    q.append(n)
        for c, dc in pdist.items():
            for dd in dirs:
                b = c + dd
                if b not in bset:
                    continue
                t = b + dd
                if t not in live or t in bset:
                    continue
                nb = bset - {b}
                nb.add(t)
                if t not in goals and frozen(t, nb, walls, goals, w):
                    continue
                ns = (b, tuple(sorted(nb)))
                nd = d + dc + 1
                if nd < best.get(ns, 1 << 60):
                    best[ns] = nd
                    parent[ns] = (s, c, dd)
                    tick += 1
                    heapq.heappush(pq, (nd, tick, ns))
    return None


def walk_path(lv, src, dst, bset):
    if src == dst:
        return ""
    prev = {src: None}
    q = collections.deque([src])
    while q:
        c = q.popleft()
        if c == dst:
            break
        for k, dd in enumerate(lv.dirs):
            n = c + dd
            if n in lv.floor and n not in bset and n not in prev:
                prev[n] = (c, k)
                q.append(n)
    out = []
    c = dst
    while prev[c] is not None:
        c, k = prev[c]
        out.append(DIR_CHARS[k])
    return "".join(reversed(out))


def reconstruct(lv, parent, s):
    steps = []
    while parent[s] is not None:
        prev, c, dd = parent[s]
        steps.append((prev, c, dd))
        s = prev
    steps.reverse()
    sol = []
    pushes = 0
    for (p, boxes), c, dd in steps:
        sol.append(walk_path(lv, p, c, set(boxes)))
        sol.append(DIR_CHARS[lv.dirs.index(dd)].upper())
        pushes += 1
    return "".join(sol)


def replay(lv, sol):
    """按解走一遍，校验确实能完成；返回走过的格子集合。"""
    p = lv.player
    boxes = set(lv.boxes)
    used = {p} | set(boxes)
    for ch in sol:
        d = lv.dirs[DIR_CHARS.index(ch.lower())]
        n = p + d
        if n not in lv.floor:
            raise ValueError("解撞墙")
        if n in boxes:
            t = n + d
            if t not in lv.floor or t in boxes:
                raise ValueError("解推不动")
            boxes.remove(n)
            boxes.add(t)
            used.add(t)
        p = n
        used.add(p)
    if boxes != lv.goals:
        raise ValueError("解未完成")
    return used


def to_rows(w, h, walls, floor, goals, boxes, player):
    """把集合重新画成 XSB，裁掉多余边框，只保留贴着地板的墙。"""
    need_wall = set()
    for c in floor:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                n = c + dx + dy * w
                if n not in floor:
                    need_wall.add(n)
    cells = floor | need_wall
    xs = [c % w for c in cells]
    ys = [c // w for c in cells]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    rows = []
    for y in range(y0, y1 + 1):
        r = []
        for x in range(x0, x1 + 1):
            i = y * w + x
            if i in floor:
                if i == player:
                    r.append("+" if i in goals else "@")
                elif i in boxes:
                    r.append("*" if i in goals else "$")
                else:
                    r.append("." if i in goals else " ")
            elif i in need_wall:
                r.append("#")
            else:
                r.append(" ")
        rows.append("".join(r).rstrip())
    return rows


# ---------------------------------------------------------------- 生成器

def random_room(rng, iw, ih):
    """在 iw×ih 内部区域随机摆放墙块，保证连通且没有死胡同。"""
    w = iw + 2
    floor = set()
    for y in range(1, ih + 1):
        for x in range(1, iw + 1):
            floor.add(y * w + x)
    # 随机削掉四角，让外形不那么方正
    for cx, cy in ((1, 1), (iw, 1), (1, ih), (iw, ih)):
        if rng.random() < 0.55:
            sx = rng.randint(1, max(1, iw // 3))
            sy = rng.randint(1, max(1, ih // 3))
            for dy in range(sy):
                for dx in range(sx):
                    x = cx + dx if cx == 1 else cx - dx
                    y = cy + dy if cy == 1 else cy - dy
                    floor.discard(y * w + x)
    shapes = [[(0, 0)], [(0, 0)], [(0, 0), (1, 0)], [(0, 0), (0, 1)],
              [(0, 0), (1, 0), (0, 1)], [(0, 0), (1, 0), (2, 0)], [(0, 0), (0, 1), (0, 2)]]
    n_blocks = rng.randint(int(iw * ih * 0.11), int(iw * ih * 0.2) + 1)
    for _ in range(n_blocks):
        shp = rng.choice(shapes)
        bx = rng.randint(1, iw)
        by = rng.randint(1, ih)
        for dx, dy in shp:
            floor.discard((by + dy) * w + bx + dx)
    dirs = (-w, w, -1, 1)
    # 反复填掉死胡同（三面是墙）
    changed = True
    while changed:
        changed = False
        for c in list(floor):
            if sum((c + d) in floor for d in dirs) <= 1:
                floor.discard(c)
                changed = True
    if not floor:
        return None
    # 只保留最大连通块
    comps = components(floor, dirs)
    floor = max(comps, key=len)
    return w, ih + 2, floor


def components(cells, dirs):
    seen = set()
    out = []
    for s in cells:
        if s in seen:
            continue
        comp = {s}
        seen.add(s)
        q = [s]
        while q:
            c = q.pop()
            for d in dirs:
                n = c + d
                if n in cells and n not in seen:
                    seen.add(n)
                    comp.add(n)
                    q.append(n)
        out.append(comp)
    return out


def reach(start, floor, bset, dirs):
    seen = {start}
    q = [start]
    while q:
        c = q.pop()
        for d in dirs:
            n = c + d
            if n in floor and n not in bset and n not in seen:
                seen.add(n)
                q.append(n)
    return seen


def reverse_farthest(w, floor, goals, rng, max_states=150_000):
    """从完成状态反向“拉箱子”做 BFS，找推数最远（最难）的初始局面。"""
    dirs = (-w, w, -1, 1)
    boxes = tuple(sorted(goals))
    bset = set(boxes)
    parent = {}
    frontier = []
    for comp in components(floor - bset, dirs):
        s = (min(comp), boxes)
        parent[s] = None
        frontier.append(s)
    depth = 0
    last = frontier
    while frontier and len(parent) < max_states:
        nxt = []
        for s in frontier:
            p, boxes = s
            bset = set(boxes)
            region = reach(p, floor, bset, dirs)
            for c in region:
                for d in dirs:
                    b = c + d
                    pc = c - d
                    if b in bset and pc in floor and pc not in bset:
                        nb = bset - {b}
                        nb.add(c)
                        np_ = min(reach(pc, floor, nb, dirs))
                        ns = (np_, tuple(sorted(nb)))
                        if ns not in parent:
                            parent[ns] = (s, b, c)
                            nxt.append(ns)
        if not nxt:
            break
        depth += 1
        last = nxt
        frontier = nxt
    if depth == 0:
        return None
    # 在最远一层里挑“换箱次数”最多的
    best = None
    sample = last if len(last) <= 60 else rng.sample(last, 60)
    for s in sample:
        pulls = []
        t = s
        while parent[t] is not None:
            prev, b, c = parent[t]
            pulls.append((c, b))   # 正向：箱子从 c 推到 b
            t = prev
        # 正向顺序就是 pulls 的顺序（从 s 往回走到完成状态）
        changes = 0
        for k in range(1, len(pulls)):
            if pulls[k][0] != pulls[k - 1][1]:
                changes += 1
        score = depth + 3 * changes
        if best is None or score > best[0]:
            best = (score, s, depth, changes)
    return best


def cluster_goals(rng, floor, n, dirs):
    """从随机一格出发长出一团相连的目标点——目标挤在一起时推箱顺序很关键，难度更高。"""
    start = rng.choice(sorted(floor))
    goals = {start}
    while len(goals) < n:
        cand = sorted({g + d for g in goals for d in dirs if g + d in floor} - goals)
        if not cand:
            return None
        goals.add(rng.choice(cand))
    return goals


def generate_one(rng, hard=False):
    if hard:
        iw = rng.randint(7, 10)
        ih = rng.randint(7, 10)
    else:
        iw = rng.randint(5, 8)
        ih = rng.randint(5, 8)
    room = random_room(rng, iw, ih)
    if room is None:
        return None
    w, h, floor = room
    lo, hi = (30, 62) if hard else (16, 42)
    if len(floor) < lo or len(floor) > hi:
        return None
    # 大片空地会让谜题变得无聊：限制 3x3 全空区域的数量
    open3 = 0
    for c in floor:
        if all(c + dx + dy * w in floor for dx in range(3) for dy in range(3)):
            open3 += 1
    if open3 > (2 if hard else 1):
        return None
    nbox = rng.choice((4, 5, 5, 6, 6, 7) if hard else (2, 3, 3, 4, 4, 5))
    if len(floor) < nbox * 6:
        return None
    if hard and rng.random() < 0.6:
        goals = cluster_goals(rng, floor, nbox, (-w, w, -1, 1))
        if goals is None:
            return None
    else:
        goals = set(rng.sample(sorted(floor), nbox))
    res = reverse_farthest(w, floor, goals, rng, max_states=400_000 if hard else 150_000)
    if res is None:
        return None
    score, (preg, boxes), depth, changes = res
    if hard:
        if depth < 34 or changes < 2 * nbox:
            return None
    elif depth < 6 + 3 * nbox or changes < nbox:
        return None
    if any(b in goals for b in boxes) and nbox <= 2:
        return None
    region = sorted(reach(preg, floor, set(boxes), (-w, w, -1, 1)))
    player = rng.choice(region)
    walls = set()
    rows = to_rows(w, h, walls, floor, goals, set(boxes), player)
    return rows, depth, changes


def trim_unused(lv, sol):
    """把解答中从未用到的死胡同格子填成墙（不影响最优解），让地图更紧凑。"""
    used = replay(lv, sol)
    floor = set(lv.floor)
    changed = True
    while changed:
        changed = False
        for c in list(floor):
            if c in used:
                continue
            if sum((c + d) in floor for d in lv.dirs) <= 1:
                floor.discard(c)
                changed = True
    return to_rows(lv.w, lv.h, set(), floor, lv.goals, lv.boxes, lv.player)


def read_levels(path, with_meta=False):
    """读取 XSB 关卡文件；'par ...' / 'sol ...' 行作为元数据。"""
    levels = []
    cur = []
    meta = {}
    title = ""

    def flush():
        nonlocal cur, meta, title
        if cur:
            levels.append((title, cur, meta) if with_meta else (title, cur))
        cur = []
        meta = {}
        title = ""

    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n").rstrip("\r")
            if line.startswith(";"):
                flush()
                title = line[1:].strip()
                continue
            if line.startswith("par ") or line.startswith("sol "):
                meta[line[:3]] = line[4:].strip()
                continue
            if not line.strip():
                flush()
                continue
            cur.append(line)
    flush()
    return levels


def difficulty(lv, moves, pushes, sol):
    changes = box_changes(lv, sol)
    return pushes + 4 * changes + 6 * len(lv.boxes) + moves * 0.15


def box_changes(lv, sol):
    changes = 0
    p = lv.player
    last_box = None
    boxes = set(lv.boxes)
    for ch in sol:
        d = lv.dirs[DIR_CHARS.index(ch.lower())]
        n = p + d
        if n in boxes:
            if last_box != n:
                changes += 1
            boxes.remove(n)
            boxes.add(n + d)
            last_box = n + d
        p = n
    return changes


def gen_worker(arg):
    seed, hard = arg
    rng = random.Random(seed)
    while True:
        g = generate_one(rng, hard)
        if g is None:
            continue
        rows, depth, changes = g
        lv = Level(rows)
        res = solve(lv, max_states=4_000_000 if hard else 1_500_000)
        if res is None:
            continue
        moves, sol = res
        # 只填掉解答没用到的死胡同，最优解不变，所以 sol 依然最优
        rows = trim_unused(lv, sol)
        replay(Level(rows), sol)
        return rows, moves, depth, changes, len(lv.boxes), sol


def cmd_gen(out, count, seed, hard=False, workers=None):
    """生成关卡，每找到一关立即追加写入 out；中断后重跑会接着已有的数量继续。"""
    import multiprocessing
    import os
    seen = set()
    if os.path.exists(out):
        seen = {"\n".join(rows) for _, rows in read_levels(out)}
    if len(seen) >= count:
        print("已有 %d 关，无需生成" % len(seen))
        return
    # 每个进程的求解器最多占用几百 MB，默认只开一半核心，避免内存吃紧
    workers = workers or max(1, (os.cpu_count() or 2) // 2)
    # 种子按已有数量偏移，续跑时不会重复之前的随机序列
    base = seed * 100000 + len(seen) * 7
    args = [(s, hard) for s in range(base, base + count * 3)]
    with multiprocessing.Pool(workers) as pool, open(out, "a", encoding="utf-8") as f:
        for rows, moves, depth, changes, nbox, sol in pool.imap_unordered(gen_worker, args):
            key = "\n".join(rows)
            if key in seen:
                continue
            seen.add(key)
            f.write("; gen pushes=%d changes=%d moves=%d\n" % (depth, changes, moves))
            f.write("par %d %d\n" % (moves, depth))
            f.write("sol %s\n" % sol)
            f.write("\n".join(rows) + "\n\n")
            f.flush()
            print("[%d/%d] boxes=%d pushes=%d changes=%d moves=%d" %
                  (len(seen), count, nbox, depth, changes, moves), flush=True)
            if len(seen) >= count:
                pool.terminate()
                break


def cmd_append(levels_path, gen_path, keep):
    """把新生成的关卡（已带最优解）按难度挑选、排序后接在现有关卡后面，原有关卡编号不变。"""
    existing = read_levels(levels_path)
    seen = {"\n".join(rows) for _, rows in existing}
    entries = []
    for title, rows, meta in read_levels(gen_path, with_meta=True):
        if "\n".join(rows) in seen:
            continue
        lv = Level(rows)
        sol = meta["sol"]
        replay(lv, sol)
        moves = len(sol)
        pushes = sum(1 for ch in sol if ch.isupper())
        changes = box_changes(lv, sol)
        diff = difficulty(lv, moves, pushes, sol)
        interest = changes / max(1, pushes) * 20 + len(lv.boxes) * 3 + pushes * 0.3
        entries.append((interest, diff, rows, moves, pushes, sol, len(lv.boxes)))
    chosen = sorted(entries, key=lambda e: e[0], reverse=True)[:keep]
    chosen.sort(key=lambda e: e[1])
    n = len(existing)
    with open(levels_path, "a", encoding="utf-8", newline="\n") as f:
        for i, (interest, diff, rows, moves, pushes, sol, nbox) in enumerate(chosen):
            f.write("; %d\n" % (n + i + 1))
            f.write("par %d %d\n" % (moves, pushes))
            f.write("sol %s\n" % sol)
            f.write("\n".join(rows) + "\n\n")
            print("#%d boxes=%d moves=%d pushes=%d diff=%.1f" % (n + i + 1, nbox, moves, pushes, diff))
    print("追加 %d 关，现共 %d 关" % (len(chosen), n + len(chosen)))


def cmd_build(hand_path, gen_path, out_path, keep_gen=None):
    entries = []
    for src, path in (("hand", hand_path), ("gen", gen_path)):
        for title, rows in read_levels(path):
            lv = Level(rows)
            res = solve(lv)
            if res is None:
                print("无解或过难，丢弃：", src, title)
                print("\n".join(rows))
                continue
            moves, sol = res
            replay(lv, sol)
            pushes = sum(1 for ch in sol if ch.isupper())
            diff = difficulty(lv, moves, pushes, sol)
            entries.append((src, title, rows, moves, pushes, diff, sol))
            print("%-4s %-28s boxes=%d moves=%3d pushes=%3d diff=%.1f" %
                  (src, title[:28], len(lv.boxes), moves, pushes, diff))
    # 标题不是数字的手工关卡是教学关，固定放在最前面
    tutorial = [e for e in entries if e[0] == "hand" and not e[1].isdigit()]
    hand = [e for e in entries if e[0] == "hand" and e[1].isdigit()]
    gen = [e for e in entries if e[0] == "gen"]
    if keep_gen is not None:
        def interest(e):
            # 偏好：频繁换箱（箱子之间互相牵制）、箱子多、推数多
            lv = Level(e[2])
            changes = box_changes(lv, e[6])
            return changes / max(1, e[4]) * 20 + len(lv.boxes) * 3 + e[4] * 0.3
        gen = sorted(gen, key=interest, reverse=True)[:keep_gen]
    allv = tutorial + sorted(hand + gen, key=lambda e: e[5])
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        for i, (src, title, rows, moves, pushes, diff, sol) in enumerate(allv):
            f.write("; %d\n" % (i + 1))
            f.write("par %d %d\n" % (moves, pushes))
            f.write("sol %s\n" % sol)
            f.write("\n".join(rows) + "\n\n")
    print("共写出 %d 关 -> %s" % (len(allv), out_path))


if __name__ == "__main__":
    if sys.argv[1] == "gen":
        cmd_gen(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 40,
                int(sys.argv[4]) if len(sys.argv) > 4 else 1)
    elif sys.argv[1] == "genhard":
        cmd_gen(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 40,
                int(sys.argv[4]) if len(sys.argv) > 4 else 1, hard=True,
                workers=int(sys.argv[5]) if len(sys.argv) > 5 else None)
    elif sys.argv[1] == "append":
        cmd_append(sys.argv[2], sys.argv[3], int(sys.argv[4]))
    elif sys.argv[1] == "build":
        cmd_build(sys.argv[2], sys.argv[3], sys.argv[4],
                  int(sys.argv[5]) if len(sys.argv) > 5 else None)
    elif sys.argv[1] == "solve":
        for title, rows in read_levels(sys.argv[2]):
            lv = Level(rows)
            print(title, solve(lv))
