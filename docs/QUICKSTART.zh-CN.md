# grainloom 中文入门

grainloom 是一个面向 monome norns / norns shield 的极简持续循环与随机切片乐器，
不需要 grid，也不需要手动触发录音。

启动后，它会立即循环录制并播放最近的一段输入。默认循环长度为 2.5 秒；每一圈
保留 72% 的旧内容，同时写入新输入，形成逐渐衰减、持续变化的 tape-delay 效果。
两条独立的 slice 播放流会从同一个实时 Buffer 中随机选择小片，以量化速度正放
或反放。

## 安装

在 norns 的 shell 中执行：

```sh
cd /home/we/dust/code
git clone https://github.com/c4pt0r/grainloom.git
```

首次安装后需要重启一次 norns 音频服务，让 SuperCollider 发现自定义引擎，然后
从 SELECT 选择 `grainloom/grainloom`。普通 Lua 更新只需重新加载 app，不需要重启
norns。

## 开箱即用

1. 将 norns 系统的 monitor level 设为 0，避免额外的干声监听路径。
2. 打开 grainloom，然后直接输入声音；录音与 sample 播放会自动开始。
3. 等待至少一圈，再调整 feedback 或 input/sample mix。
4. 调高 tape/slice mix，可以听到更多随机切片。
5. K2 冻结当前 Buffer；K3 开关 sample 播放。
6. 冻结状态下长按 K2 进入 dub，往循环上叠新的一层；再按一下 K2 结束。

## 控制

E1 换页，E2 / E3 调整当前页的两个参数。

| 页面 | E2 | E3 |
| --- | --- | --- |
| LOOP | 录音长度（20 毫秒–30 秒） | feedback（0–0.98） |
| WINDOW | 窗口起点 | 窗口长度（1–100%） |
| TAPE | tape 速度与方向（-2×–2×） | input/sample 比例 |
| REGEN | regen | regen 音色（200 Hz–8 kHz） |
| SLICE | slice 长度（0.06–0.5 秒） | slice 密度（1–8 Hz） |
| SLICE POS | slice 年龄 | slice 散布 |
| SLICE PLAY | 量化速度上限（0.5×–2×） | 反放概率 |
| LEVEL | tape/slice 比例 | sample level（0.25×–4×） |
| BLOOM | bloom | bloom 时间（0.5–10 秒） |

- **录音长度**最短到 20 毫秒。低于大约 50 毫秒时，这条循环不再是一个乐句而是
  一个波形：重复本身就是音高，feedback 成了它的衰减，dub 成了往上做加法合成。
- **WINDOW** 把 tape 读头限制在 buffer 的一段里。窗口收窄就是 stutter，放开就
  回到完整循环；写头始终在底下跑完整圈，所以窄窗口里的内容一直被新材料替换。
- **SLICE POS** 的位置锚定在移动的写头上，所以「年龄」指的是往过去回溯多远，
  而不是 buffer 里一个固定的点。年龄 0、散布 1 就是过去那种全域均匀随机。
- **REGEN** 把 tape 读头折回录音输入，这正是变速产生「累积」的原因：只要速度
  不是 1×，每一圈都会被移调后重新录下，循环就顺着自己往上爬或往下沉。防止它
  失控的有三件事 —— `regen 音色` 是折回路径里的低通且永不全开，所以每一代都掉
  高频，向上的螺旋会撞墙而不是堆到 Nyquist；2× 的录音补偿只作用于硬件输入而不
  作用于折回，否则环路增益翻倍；dub 期间 regen 被完全关闭，因为 dub 已经完整
  保留上一圈，再叠自己的输出必然超过 1。数值内部还额外封顶在 1 以下。
- E1 翻页会从最后一页绕回第一页。
- **BLOOM** 让机器回应沉默：输入停下后，跟随器在 bloom 时间内落下，slice 随之
  变长、变稀、并占据更多比例；你一出声它就退回去。默认 0，完全不介入。

- **K2 — freeze**：短按冻结或恢复录音写头。冻结时已有循环继续播放。
- **K2 — 长按 0.5 秒（需在冻结状态）**：进入 dub。写头重新运行，但已有内容
  完整保留，新输入按 `dub level` 叠加上去，而不是和旧内容交叉淡化。dub 期间
  按任意一下 K2 即结束并重新冻结。写头正在运行时长按不会进入 dub。

因为 K2 现在承担两个手势，它的 freeze 切换改为在**松手**时触发。
- **K3 — on/off**：打开或关闭 tape 与 slice 的 sample 播放。只要 mix 中仍包含
  input，干声就继续存在。

input/sample mix 显示为 `输入:sample`：`10:0` 只有输入，`5:5` 为等比例，
`0:10` 只有 sample。

**录音长度是一个回绕点，不是 buffer 大小**，所以改它在下一个采样就生效：不重新
分配、不清空、没有断音，可以在演奏中连续扫。缩短会立刻把循环重新框到它的开头；
加长则用已有内容的重复来延展，再由新输入一圈圈覆盖掉。

## 默认值

- 循环：2.5 秒
- feedback：0.72
- tape 速度：1×
- input/sample：2:8
- slice 长度：0.2 秒
- slice 密度：5 Hz
- slice 速度上限：1.5×
- reverse 概率：35%
- tape/slice：50%
- sample level：1.5×
- output level：0.75（可从 PARAMETERS 调整）

## Slice 速度

随机 slice 不使用任意连续速度，而是从以下 tape-speed 倍率中选择：

`0.5× · 2/3× · 0.75× · 1× · 4/3× · 1.5× · 2×`

`quantized speed max` 决定当前可选择的最高倍率；`reverse chance` 独立决定每个
slice 是否反放。slice 只进入输出混音，不会写回 feedback Buffer，因此不会因
切片重叠造成递归增益。

## 当前边界

- 循环 Buffer 为单声道；slice 输出带随机立体声声像。
- 不加载文件，也不保存 Buffer。保存演奏请使用 norns 的 TAPE 录音功能。
- 没有 generation loss、dropout、wow/flutter、bit-crush、glitch、内部混响或
  generation printing。
- grainloom 不会修改 norns 的全局 reverb。

详细实现和检查方法见 `docs/ALGORITHMS.md` 与 `docs/VALIDATION.md`。
