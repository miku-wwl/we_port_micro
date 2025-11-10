# 构建出gRPC需要生成的代码，但是无法使用 定位0.5小时
![alt text](image-issue1.png)

![alt text](image-issue2.png)

要解决这个问题，我们可以从IDE 源根配置、构建工具（Gradle）配置和IDE 缓存索引三个维度分析：
1. 检查「源根（Sources Root）」配置
IntelliJ IDEA 需要将 gRPC 生成的代码目录标记为Sources Root，才能识别为可编译的源码。
第一个项目中，grpc [main] 已被标记为「sources root」（有明确标识）；
第二个项目中，右键点击 grpc 目录 → 选择 Mark Directory as → Sources Root，使其被标记为源码根目录（目录会显示蓝色标识）。

# 另一个测试用的 win10 电脑，无法生成gRPC代码 定位1.5小时，是gradle cache路径含有中文名导致

# 解决Win10环境下因Gradle缓存路径含中文名导致gRPC代码无法生成的问题
**问题描述**：测试用Windows 10电脑无法生成gRPC代码，经1.5小时定位，确定根因为Gradle缓存路径包含中文名。  
**解决方案**：将Gradle缓存路径修改为纯英文路径，以下是全局生效的详细操作步骤，修改后所有Gradle项目均会使用新缓存路径，彻底规避中文路径引发的缓存读取失败问题。

## 核心原理
通过配置`GRADLE_USER_HOME`环境变量，将Gradle的缓存目录指定到纯英文路径（如`D:\GradleCache\.gradle`），以此替代默认存放在中文用户名下的路径（通常为`C:\Users\中文用户名\.gradle`）。

## 详细操作步骤
### 步骤1：创建新的英文缓存目录
新建一个**无中文、无空格、无特殊符号**的文件夹作为新缓存路径，具体要求如下：
1. 推荐路径：`D:\GradleCache\.gradle`
    - 注意：`.gradle`是文件夹名称，并非文件后缀，直接创建即可
2. 其他可选路径：`E:\DevTools\Gradle\.gradle`（需确保所有父目录均为英文）

| 类型 | 示例 | 说明 |
| ---- | ---- | ---- |
| ✅ 正确示例 | `D:\GradleCache\.gradle` | 全英文路径，无特殊字符 |
| ❌ 错误示例1 | `D:\编程工具\Gradle缓存\.gradle` | 路径包含中文 |
| ❌ 错误示例2 | `D:\Gradle Cache\.gradle` | 路径包含空格 |

### 步骤2：配置系统环境变量（全局生效，推荐）
该方式对所有Gradle项目生效，一次配置长期可用，操作步骤如下：
1. 打开系统环境变量设置（两种方法任选其一）
    - 方法1：桌面右键点击「此电脑」→ 选择「属性」→ 点击「高级系统设置」→ 进入「环境变量」
    - 方法2：按下`Win+R`组合键打开运行窗口，输入`sysdm.cpl`回车 → 切换至「高级」选项卡 → 点击「环境变量」
2. 新增系统变量
    1. 在「系统变量」区域（**切勿选择用户变量**，避免中文用户目录干扰）点击「新建」
    2. 变量名：`GRADLE_USER_HOME`（固定写法，大小写不敏感，建议原样复制）
    3. 变量值：粘贴步骤1创建的英文缓存路径（如`D:\GradleCache\.gradle`）
    4. 连续点击「确定」保存配置
3. 验证环境变量是否生效
    1. 必须打开**新的PowerShell窗口**（旧窗口不加载新配置）
    2. 输入以下命令并执行：
    ```powershell
    echo $env:GRADLE_USER_HOME
    ```
    3. 若输出为步骤1设置的路径（如`D:\GradleCache\.gradle`），则配置成功

### 步骤3：（可选）项目级配置（仅当前项目生效）
若无需全局修改，仅针对当前项目调整，可按以下操作配置：
1. 找到项目根目录，创建`gradle.properties`文件（若已存在则直接打开）
2. 添加如下配置并保存：
    ```properties
    # 配置Gradle缓存路径（替换为你的英文路径）
    org.gradle.cache.dir=D:\\GradleCache\\.gradle
    ```
⚠️ 重要提示：路径中的反斜杠需写两个（`\\`），或改用正斜杠（`/`），例如`D:/GradleCache/.gradle`

### 步骤4：验证缓存路径是否生效
1. 清理旧缓存（可选，建议操作）
    - 无需手动删除原中文路径下的旧缓存，新配置会自动优先使用新路径
2. 测试Gradle构建并验证
    1. 进入项目根目录，在PowerShell中执行以下命令，重新下载依赖至新缓存路径：
        ```powershell
        ./gradlew clean build --refresh-dependencies
        ```
    2. 双重验证生效状态：
        - 打开步骤1创建的`D:\GradleCache\.gradle`文件夹，若自动生成`caches`、`wrapper`等子文件夹，说明缓存路径已切换
        - 查看构建日志，若出现`Using Gradle user home: D:\GradleCache\.gradle`，则确认配置生效

### 步骤5：重新执行项目构建
在PowerShell中执行以下命令，完成项目构建：
```powershell
./gradlew build
```
此时Gradle会从新的英文缓存路径读取或下载`protoc`与`protoc-gen-grpc-java`插件，不会再因中文用户名出现「找不到路径」的问题，gRPC代码可正常生成。