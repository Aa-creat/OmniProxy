# 全局开发与环境规范 (Global Environment & CI/CD Rules)

适用范围：所有开发项目与会话，后续 AI 助手及工具链必须严格遵循本规范。

---

## 1. 终端 Shell 调用铁律 (PowerShell 7 / pwsh)
- **强制使用 `pwsh`**：在 Windows 环境下执行任何 PowerShell 相关脚本、管道或命令时，**必须统一优先调用 `pwsh` (PowerShell 7)**，严禁直接调用系统内置的旧版 Windows PowerShell 5.1 (`powershell.exe`)。
- **UTF-8 编码安全**：`pwsh` 原生采用标准 UTF-8（无 BOM）输入输出，杜绝旧版 PS 5.1 默认 UTF-16 LE 导致的字符集混乱、代码文件损坏或解析错误。

---

## 2. GitHub Actions 额度节约铁律 (CI/CD Quota Protection)
- **严禁全量 Branch 自动触发**：由于 GitHub 账户 Actions 执行分钟数额度有限，所有项目的构建与发布工作流（尤其是 Android NDK、Go、Docker 等重型交叉编译任务）**严禁**监听日常分支的常规 push（如 `push: branches: [main, master, dev]`）。
- **仅限 Release/Tag 按需触发**：工作流触发条件必须严格限定为：
  1. `push: tags: ['v*']`（打版本发布 Tag 时按需触发）
  2. `workflow_dispatch:`（保留在 GitHub 网页控制台手动点击触发的备用入口）
- 确保日常代码提交、文档修缮与常规 push 实现 **0 消耗** Actions 额度。
