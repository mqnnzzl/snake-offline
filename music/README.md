# 霓虹果园 / Neon Orchard

为 SNAKE_ 像素街机版编写的原创纯音乐。128 BPM，D 小调，32 小节，60 秒循环。
脉冲波主旋律、三角波低音、左右声道分解和弦与合成鼓组；四个八小节段落有旋律与配器变化。
全部音符与音色由 ComposeMusic.java 生成，没有使用外部歌曲、采样包或音频素材。

运行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\compose-music.ps1` 可重新生成。
输出为 32kHz、16bit、双声道 WAV，保存于 app/src/main/res/raw 和 dist。
循环回声跨越曲末；首尾各4ms去爆音包络。峰值约 -1.72 dBFS。
