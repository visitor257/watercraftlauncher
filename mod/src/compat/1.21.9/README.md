# compat/1.21.9 —— 1.21.9 / 1.21.10 / 1.21.11 共用

一个目录盖三个版本，因为它们之间**没有任何 API 差异**（实测：同一份覆盖文件在这三个版本上
都 `BUILD SUCCESSFUL`，界面也都真窗口验过）。目录名取这一代里最老的那个版本。

| 需要覆盖的文件 | 为什么 |
|---|---|
| `ui/ChromeScreen.java` | 1.21.9 起 `Screen#keyPressed` 从 `(int,int,int)` 变成 `keyPressed(KeyInput)`；这里把 `KeyInput` 拆回三个 int 交给 `keyPressedDefault` |
| `McVerClient.java` | `new KeyBinding(..., "category.mcver")` 的第 4 个参数变成 `KeyBinding.Category`（record），要用 `Category.create(Identifier)` 造 |
| `core/Compat.java` | `ScreenshotRecorder.saveScreenshot` 多了第 4 个 int 参数（缩放倍数） |

编译器的原始报错（别猜，照抄）：

```
error: method keyPressed in class Screen cannot be applied to given types;
  required: KeyInput

error: incompatible types: String cannot be converted to Category

error: no suitable method found for saveScreenshot(File,String,Framebuffer,(t)->{ })
```

## 以后再加 1.21.x

先看 `Screen#keyPressed` 的参数个数：

- 3 个 int → 主源码那套（`compat=` 留空）
- `KeyInput` → 直接用本目录

这个判断比「版本号邻近」可靠得多：1.21.4 → 1.21.5 之间渲染管线也改过，但键盘没改；
1.21.9 才换键盘。先用编译器问一遍，再决定要不要新目录。
