# MMCKB 构建签名

调试包和发布包均配置为使用同一份 MMCKB 签名证书。私钥、`release.keystore` 和 `signing.properties` 均被 `.gitignore` 排除，不能提交到仓库。

## 当前证书标识

| 项目 | 值 |
|---|---|
| 证书主体 | `CN=MMCKB, OU=MMCKB, O=MMCKB, L=Internet, ST=Internet, C=CN` |
| Alias | `MMCKB` |
| SHA-256 指纹 | `5A:23:B7:12:42:DE:11:FC:5B:CB:BF:3A:E2:E0:4F:0A:64:AB:E1:48:98:8C:3D:A4:14:0F:57:A9:D8:25:86:12` |

## 本地构建

将私有 `release.keystore` 放入 `app/`，并在仓库根目录建立被忽略的 `signing.properties`：

```properties
storeFile=release.keystore
storePassword=<私有口令>
keyAlias=MMCKB
keyPassword=<私有口令>
```

`assembleDebug` 与 `assembleRelease` 都会使用这套签名。请在任何安装或升级前用 `apksigner verify --print-certs` 比对上方 SHA-256 指纹。

## GitHub Actions

工作流读取以下 GitHub Actions 机密。应将本地相同证书转换为 Base64 后保存，所有字段必须与同一份 MMCKB 证书匹配：

| 机密 | 内容 |
|---|---|
| `KEYSTORE_BASE64` | `app/release.keystore` 的 Base64 内容 |
| `KEYSTORE_PASSWORD` | 密钥库口令 |
| `KEY_ALIAS` | `MMCKB` |
| `KEY_PASSWORD` | 私钥口令 |

> 请仅通过 GitHub 的仓库机密界面或具备 Actions secrets 写入权限的令牌更新这些值，切勿将口令或 Base64 私钥写入 Issue、提交、README 或发布日志。
