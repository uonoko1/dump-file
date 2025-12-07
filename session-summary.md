# ヒープダンプ学習セッション要約

このファイルは Claude Code との対話内容を要約したもの。
別の PC で作業を継続する際の引き継ぎ資料として使用する。

---

## プロジェクトの目的

```
ヒープダンプについて学習し、技術ブログを書く

- ヒープダンプとは何か
- どうやって取得するか
- どうやって解析するか
- どんな時に使うか
```

---

## 完了した作業

### 1. 環境構築

- **DevContainer** で開発環境を構築
  - JDK 17 (Eclipse Temurin)
  - sbt (Scala ビルドツール)
  - Node.js
  - Eclipse MAT CLI版
- Claude Code のセッション永続化のため、ホストパスと一致させた
  - `~/.claude` と `~/.claude.json` をマウント
  - workspaceFolder を `/home/uonoko/Development/blog/dump-file` に設定

### 2. サンプルアプリケーション作成

- **Scala + Akka HTTP** でメモリリークを起こすアプリを作成
- ファイル: `scala-heap-demo/src/main/scala/example/LeakyApp.scala`
- 意図的なリークポイント:
  - `sessionCache` - セッション情報を無制限に保持
  - `requestLogs` - リクエストログを無制限に追加
  - `heavyDataCache` - 大きなデータを無制限にキャッシュ

### 3. 負荷テストとOOM発生

- `load-test.sh` スクリプトで負荷をかけた
- OOM (OutOfMemoryError) を発生させた
- ヒープダンプが自動生成された: `/tmp/java_pid16610.hprof`

### 4. ヒープダンプ解析（CLI版）

- Eclipse MAT の `ParseHeapDump.sh` でレポート生成
- 生成されたレポート:
  - `Leak_Suspects.zip`
  - `System_Overview.zip`
  - `Top_Components.zip`
- 解析結果: `heavyDataCache` (HashMap) が 74MB を占有していることを特定

### 5. ヒープダンプ解析（GUI版）

- Windows に Eclipse MAT GUI版をインストール
- Eclipse Temurin JDK 17 を Windows にインストール
- .hprof ファイルの権限を変更 (`chmod 644`)
- GUI で Leak Suspects, Histogram, Dominator Tree, Path To GC Roots を確認

### 6. ドキュメント作成

- `heap-dump-analysis-guide.md` - 解析ガイド（CLI版・GUI版両方）
- `devcontainer-claude-code-setup.md` - DevContainer 設定ガイド

---

## 学習した主要概念

### ヒープダンプ関連

| 概念 | 説明 |
|------|------|
| ヒープダンプ (.hprof) | JVM のメモリスナップショット |
| Shallow Heap | オブジェクト自身のサイズ |
| Retained Heap | 解放したら空くサイズ |
| GC Roots | ガベージコレクションの起点 |
| Dominator Tree | メモリ支配の階層構造 |
| Path To GC Roots | なぜ GC されないかの参照チェーン |

### Eclipse MAT

| 項目 | 説明 |
|------|------|
| CLI版 | ParseHeapDump.sh でレポート生成（サーバー向け） |
| GUI版 | MemoryAnalyzer で対話的に解析（詳細調査向け） |
| 両者の違い | 解析エンジンは同じ、操作方法が異なる |

### JVM オプション

| オプション | 意味 |
|-----------|------|
| `-XX:+HeapDumpOnOutOfMemoryError` | OOM 時に自動でダンプ取得 |
| `-XX:HeapDumpPath=/tmp` | ダンプファイルの保存先 |
| `-Xmx128m` | 最大ヒープサイズ |

### コマンド

| コマンド | 用途 |
|---------|------|
| `jps -l` | Java プロセス一覧 |
| `jmap -dump:format=b,file=dump.hprof <PID>` | ヒープダンプ取得 |
| `jcmd <PID> GC.heap_dump dump.hprof` | ヒープダンプ取得（推奨） |

---

## 議論した内容

### ブログの構成について

```
おすすめ構成:

1. ヒープダンプとは何か（導入）
2. ヒープダンプの取得方法
3. 解析方法（GUI版）← メインで詳しく（スクリーンショット付き）
4. 解析方法（CLI版）← サブで紹介（サーバー環境向け）
5. 用語解説
6. まとめ

理由:
- GUI版は視覚的でわかりやすく、初心者向け
- CLI版はサーバー環境で必須なので補足として紹介
- 両方載せることで幅広い読者に対応
```

### HashMap の内部構造

```
HashMap
  └── Node[512]  （バケット配列）
        ├── Node("key1" → value1)
        ├── Node("key2" → value2)
        └── ...

- バケット = ハッシュ値でデータを振り分ける箱
- 同じキーで put → 上書き（2つの値は保持されない）
- ハッシュ関数は決定的（同じ入力→同じ出力）
```

### Histogram の >= 表記

```
Retained Heap に >= がつく理由:
- 参照の重複がカウントされている
- 正確な値ではなく「以上」の意味
- 本当のメモリ使用量は Shallow Heap を見る
```

### Path To GC Roots の目的

```
目的: 「どのクラスのどの変数がオブジェクトを保持しているか」を特定

HashMap @ 0xfa7f8110  ← メモリアドレスだけではコード上の場所がわからない
    ↓ Path To GC Roots
example.LeakyApp$ の heavyDataCache  ← これでコードを修正できる
```

---

## 残りの作業

- [ ] アプリケーションを修正してリークを解消
- [ ] Node.js のヒープダンプについて学習（当初の予定に含まれていた）
- [ ] ブログ記事の執筆

---

## ファイル構成

```
/home/uonoko/Development/blog/dump-file/
├── .devcontainer/
│   ├── Dockerfile              # JDK, sbt, MAT CLI をインストール
│   └── devcontainer.json       # Claude Code セッション永続化設定
├── scala-heap-demo/
│   ├── src/main/scala/example/
│   │   └── LeakyApp.scala      # メモリリークするサンプルアプリ
│   ├── build.sbt
│   ├── load-test.sh            # 負荷テストスクリプト
│   ├── java_pid16610.hprof     # 取得したヒープダンプ
│   └── leak_reports/           # 解凍したレポート
├── heap-dump-analysis-guide.md # 解析ガイド（メイン成果物）
├── devcontainer-claude-code-setup.md
└── session-summary.md          # このファイル
```

---

## 別の PC で作業を再開するには

1. リポジトリを clone
2. DevContainer を起動
3. このファイル (`session-summary.md`) を Claude Code に読ませる
4. 「続きをやって」と伝える

```bash
# Claude Code で読み込む例
cat session-summary.md
# または Claude Code 内で
# 「session-summary.md を読んで、作業を継続して」
```
