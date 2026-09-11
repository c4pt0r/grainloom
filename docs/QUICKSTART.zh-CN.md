# grainloom 中文入门

颗粒采样、循环、磁带损耗、glitch 与混响的混合乐器。
面向原版 norns / norns shield，不需要 grid。

**v0.1.0 为实验版：桌面代码与离线音频测试已通过，尚未在 norns 实机验证。**

## 安装

在 norns 的 shell 中执行：

```sh
git clone https://github.com/c4pt0r/grainloom.git /home/we/dust/code/grainloom
```

也可以下载 Release 中的 ZIP，将 `grainloom` 文件夹通过 SFTP 放进
`/home/we/dust/code/`。重启 norns 编译引擎，再从 SELECT 选择
`grainloom/grainloom`。不要安装多份同名引擎。

## 操作

- **E1**：换页；**E2 / E3**：调整页面上的两个参数。
- **K2**：开始新录音；再按一次提前结束；默认最多录制 8 秒，可调到 30 秒。
- **K3**：冻结／恢复颗粒扫描。普通循环层继续运行。
- **短按住 K1 + K2**：选择采样文件。
- **短按住 K1 + K3**：播放／暂停。暂停保留混响尾音，播放头继续前进。
- **PARAMETERS → print next generation**：将处理后的声音重录为下一代采样。

K1 长按由 norns 系统菜单使用。加载采样也可以从 PARAMETERS 进入。

## 磁带损耗

TAPE LOSS 页控制损耗和 dropout，INSTABILITY 页控制 wow/flutter。
PATINA 页的数字降质单独控制，避免将磁带质感等同于 bitcrusher。

每次 print 都会将当前颗粒、glitch、磁带损耗与混响印入采样，反复执行即可
累积损耗。屏幕 `g` 显示完成的重录代数。新采样仍会经过当前效果链。

重录会替换内存里的采样，第一版没有撤销。磁盘原始文件不会被修改。
PARAMETERS 预设只保存参数，不保存录音；保存演奏请用 norns 的 TAPE 录音功能。

## 第一版边界

采样内部为单声道，颗粒声像和混响输出为立体声。输入录音混合左右声道，
加载立体声文件取第一个声道。内部重录也会折叠成单声道。

这是原创、公开算法的磁带与颗粒乐器，不是 Generation Loss、MOOD 或 Morphagene
私有算法的精确复刻。详细算法与验证范围见英文 README 和 docs。
