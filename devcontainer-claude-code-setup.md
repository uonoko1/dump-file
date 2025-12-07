# Dev Container で Claude Code のセッションを共有・永続化する方法

Dev Container 内でホストの Claude Code セッションを引き継ぎ、会話履歴を永続化する方法をまとめる。

## 前提知識：Claude Code が使用するファイル

| パス | 役割 | 共有必要？ |
|------|------|:---:|
| `~/.claude.json` | OAuth 認証トークン、MCP設定、テーマ設定 | ✅ 必須 |
| `~/.claude/` | 会話履歴、コマンド履歴、プロジェクト設定 | ✅ 必須 |
| `~/.claude/projects/` | プロジェクトごとの会話履歴（パス名がキー） | ✅ 必須 |
| `.claude/` (プロジェクト内) | プロジェクトローカル設定 | 自動（workspace内） |

### 重要なポイント

`~/.claude/projects/` 内の会話履歴は **プロジェクトの絶対パス** をキーとして保存される：

```
~/.claude/projects/
├── -home-uonoko-Development-myproject/   ← /home/uonoko/Development/myproject の履歴
└── -home-uonoko-another-project/         ← /home/uonoko/another-project の履歴
```

そのため、コンテナ内のワークスペースパスをホストと同じにする必要がある。

---

## セットアップ手順

### 1. ディレクトリ構成

```
your-project/                    ← ホストで claude を起動するディレクトリ
├── .devcontainer/
│   ├── Dockerfile
│   └── devcontainer.json
├── .claude/                     ← プロジェクトローカル設定（自動生成）
└── (その他のプロジェクトファイル)
```

### 2. Dockerfile

```dockerfile
FROM eclipse-temurin:17-jdk  # ベースイメージは用途に応じて変更

# 必要なツールをインストール
RUN apt-get update && apt-get install -y \
    curl \
    gnupg \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Claude Code インストール
RUN npm install -g @anthropic-ai/claude-code

# ホストと同じパス構造を作成（重要！）
# ここをホストのプロジェクトパスに合わせる
RUN mkdir -p /home/YOUR_USERNAME/path/to/your-project

WORKDIR /home/YOUR_USERNAME/path/to/your-project
```

### 3. devcontainer.json

```json
{
  "name": "Your Project",
  "build": {
    "dockerfile": "Dockerfile"
  },
  "mounts": [
    {
      "source": "${localEnv:HOME}/.claude",
      "target": "/root/.claude",
      "type": "bind"
    },
    {
      "source": "${localEnv:HOME}/.claude.json",
      "target": "/root/.claude.json",
      "type": "bind"
    }
  ],
  "workspaceMount": "source=${localWorkspaceFolder},target=/home/YOUR_USERNAME/path/to/your-project,type=bind",
  "workspaceFolder": "/home/YOUR_USERNAME/path/to/your-project",
  "remoteUser": "root"
}
```

### 4. 設定のカスタマイズ

以下を自分の環境に合わせて変更する：

| 項目 | 例 | 説明 |
|------|-----|------|
| `YOUR_USERNAME` | `uonoko` | ホストのユーザー名 |
| `path/to/your-project` | `Development/blog/dump-file` | ホストのプロジェクトパス |

---

## 使い方

### 初回セットアップ

```bash
# ホストで Claude Code を起動（認証を済ませておく）
cd /home/YOUR_USERNAME/path/to/your-project
claude

# VSCode で Dev Container を起動
code .
# → "Reopen in Container" を選択
```

### コンテナ内での使用

```bash
# 会話を再開
claude --resume

# または新規セッション開始
claude
```

---

## トラブルシューティング

### 「No conversations found to resume」と表示される

**原因**: コンテナ内のワークスペースパスがホストと一致していない

**確認方法**:
```bash
# ホストで確認
ls ~/.claude/projects/
# → -home-uonoko-Development-myproject のような形式で表示される

# コンテナ内で確認
pwd
# → ホストと同じパスになっているか確認
```

**解決方法**: `devcontainer.json` と `Dockerfile` のパスを修正

### 認証エラーが発生する

**原因**: `~/.claude.json` がマウントされていない

**確認方法**:
```bash
# コンテナ内で確認
cat /root/.claude.json
```

**解決方法**: `devcontainer.json` の `mounts` に `~/.claude.json` が含まれているか確認

### パーミッションエラー

**原因**: マウントしたファイルの所有者が異なる

**解決方法**: `remoteUser` を `root` に設定するか、適切な権限を付与

---

## 設定ファイルの優先順位

Claude Code は以下の優先順位で設定を読み込む（高い順）：

1. エンタープライズ管理ポリシー
2. コマンドラインフラグ
3. `.claude/settings.local.json`（プロジェクト個人）
4. `.claude/settings.json`（プロジェクト共有）
5. `~/.claude/settings.json`（ユーザーグローバル）

---

## 完全な設定例

この例は `/home/uonoko/Development/blog/dump-file` でのセットアップ：

### Dockerfile

```dockerfile
FROM eclipse-temurin:17-jdk

RUN apt-get update && apt-get install -y \
    curl \
    gnupg \
    apt-transport-https \
    && echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list \
    && curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get update \
    && apt-get install -y sbt nodejs \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN npm install -g @anthropic-ai/claude-code

RUN mkdir -p /home/uonoko/Development/blog/dump-file

WORKDIR /home/uonoko/Development/blog/dump-file
```

### devcontainer.json

```json
{
  "name": "Scala Heap Demo",
  "build": {
    "dockerfile": "Dockerfile"
  },
  "mounts": [
    {
      "source": "${localEnv:HOME}/.claude",
      "target": "/root/.claude",
      "type": "bind"
    },
    {
      "source": "${localEnv:HOME}/.claude.json",
      "target": "/root/.claude.json",
      "type": "bind"
    }
  ],
  "workspaceMount": "source=${localWorkspaceFolder},target=/home/uonoko/Development/blog/dump-file,type=bind",
  "workspaceFolder": "/home/uonoko/Development/blog/dump-file",
  "forwardPorts": [8080],
  "customizations": {
    "vscode": {
      "extensions": [
        "scala-lang.scala",
        "scalameta.metals"
      ]
    }
  },
  "remoteUser": "root"
}
```
