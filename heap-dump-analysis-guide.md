# ヒープダンプ解析ガイド

Eclipse MAT を使ったヒープダンプの取得から解析までの手順をまとめる。

---

## 目次

1. [ヒープダンプの取得](#1-ヒープダンプの取得)
2. [レポートの出力（Eclipse MAT）](#2-レポートの出力eclipse-mat)
3. [レポートの解析方法（CLI版）](#3-レポートの解析方法)
4. [用語解説](#4-用語解説)
5. [よくあるリークパターンと修正方法](#5-よくあるリークパターンと修正方法)
6. [GUI版 MAT での解析手順](#6-gui版-mat-での解析手順)
7. [コマンドまとめ（コピペ用）](#7-コマンドまとめコピペ用)

---

# 1. ヒープダンプの取得

ヒープダンプ（.hprof ファイル）を取得する方法は3つある。

## 方法A: OOM 発生時に自動取得（推奨）

JVM 起動時にオプションを設定しておく:

```bash
# sbt の場合
sbt -J-XX:+HeapDumpOnOutOfMemoryError \
    -J-XX:HeapDumpPath=/tmp \
    run

# java コマンドの場合
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/tmp \
     -jar app.jar
```

| オプション | 意味 |
|-----------|------|
| `-XX:+HeapDumpOnOutOfMemoryError` | OOM 発生時に自動でダンプを取得 |
| `-XX:HeapDumpPath=/tmp` | ダンプファイルの保存先 |

OOM 発生時に `/tmp/java_pid<PID>.hprof` が生成される。

---

## 方法B: jmap で手動取得（稼働中のプロセス）

```bash
# 1. プロセスID を確認
jps -l
# 出力例:
# 12345 example.LeakyApp

# 2. ヒープダンプを取得
jmap -dump:format=b,file=/tmp/heapdump.hprof 12345
```

---

## 方法C: jcmd で手動取得（稼働中のプロセス）

```bash
# 1. プロセスID を確認
jps -l

# 2. ヒープダンプを取得
jcmd 12345 GC.heap_dump /tmp/heapdump.hprof
```

---

## jmap vs jcmd

| コマンド | 特徴 |
|---------|------|
| `jmap` | 古くからあるツール、シンプル |
| `jcmd` | 新しいツール、より多機能（推奨） |

---

# 2. レポートの出力（Eclipse MAT）

## Eclipse MAT とは

**Eclipse Memory Analyzer Tool (MAT)** = ヒープダンプを解析するためのツール

Eclipse Foundation が開発したオープンソースツールで、メモリリークの検出に特化している。

---

## GUI版 vs CLI版

Eclipse MAT には2つの使い方がある:

```
Eclipse MAT（同じツール）
├── GUI版: MemoryAnalyzer（デスクトップアプリ）
│           → ダブルクリックで起動、画面で操作
│
└── CLI版: ParseHeapDump.sh（シェルスクリプト）
            → コマンドラインから実行、レポート自動生成
```

| 項目 | GUI版 | CLI版 |
|------|-------|-------|
| **解析エンジン** | 同じ | 同じ |
| **解析できる範囲** | 同じ | 同じ |
| **操作方法** | 対話的にクリック | コマンドで実行 |
| **出力** | 画面に表示 | HTML レポート（ZIP） |
| **用途** | ローカルで詳細調査 | サーバー上で自動解析 |

```
【CLI版の動作】
.hprof ファイル
      │
      ▼ ParseHeapDump.sh で処理
      │
      ▼ 静的レポート生成
┌─────────────────────────────┐
│  Leak_Suspects.zip          │
│  System_Overview.zip        │
│  Top_Components.zip         │
└─────────────────────────────┘
      │
      ▼ 解凍して HTML を読む


【GUI版の動作】
.hprof ファイル
      │
      ▼ MAT で直接開く
      │
┌─────────────────────────────┐
│  対話的に操作               │
│  - クリックして深掘り       │
│  - その場でクエリ実行       │
│  - フィルタ適用             │
└─────────────────────────────┘
```

---

## ParseHeapDump.sh とは

**ParseHeapDump.sh = Eclipse MAT の CLI モード起動スクリプト**

```
Eclipse MAT をインストールすると含まれているスクリプト

/opt/mat/
├── MemoryAnalyzer        ← GUI版の実行ファイル
├── ParseHeapDump.sh      ← CLI版のスクリプト（これ）
├── plugins/              ← 共通のエンジン（両方で使用）
└── ...

内部的には:
  → MAT のエンジン（Java）をヘッドレスモードで起動
  → 指定されたレポートを生成して終了
```

---

## レポート生成コマンド

```bash
# ParseHeapDump.sh でレポートを生成
/opt/mat/ParseHeapDump.sh /tmp/java_pid12345.hprof \
  org.eclipse.mat.api:suspects \
  org.eclipse.mat.api:overview \
  org.eclipse.mat.api:top_components
```

| オプション | 生成されるレポート |
|-----------|-------------------|
| `org.eclipse.mat.api:suspects` | Leak_Suspects.zip（リーク疑い） |
| `org.eclipse.mat.api:overview` | System_Overview.zip（全体概要） |
| `org.eclipse.mat.api:top_components` | Top_Components.zip（大きいコンポーネント） |

---

## 生成されるレポート

実行後、同じディレクトリに ZIP ファイルが生成される:

```
/tmp/
├── java_pid12345.hprof                          ← 元のダンプ
├── java_pid12345.hprof_Leak_Suspects.zip        ← 生成されたレポート
├── java_pid12345.hprof_System_Overview.zip
└── java_pid12345.hprof_Top_Components.zip
```

| レポート | 内容 |
|----------|------|
| **System_Overview.zip** | ヒープ全体の概要、クラス別使用量 |
| **Leak_Suspects.zip** | リークの疑いがあるオブジェクトを自動検出 |
| **Top_Components.zip** | 最大のコンポーネントの詳細 |

---

## ZIP を解凍

```bash
# 解凍先ディレクトリを作成
mkdir -p leak_reports

# 各レポートを解凍
unzip -o /tmp/java_pid*.hprof_Leak_Suspects.zip -d leak_reports/leak_suspects
unzip -o /tmp/java_pid*.hprof_System_Overview.zip -d leak_reports/system_overview
unzip -o /tmp/java_pid*.hprof_Top_Components.zip -d leak_reports/top_components
```

解凍後の構造:

```
leak_reports/
├── leak_suspects/
│   ├── index.html          ← メインページ
│   ├── pages/              ← 詳細ページ
│   └── icons/              ← アイコン画像
├── system_overview/
│   └── ...
└── top_components/
    └── ...
```

---

## レポートを閲覧

### 方法A: ブラウザで開く（推奨）

```bash
# ローカル環境の場合
open leak_reports/leak_suspects/index.html        # macOS
xdg-open leak_reports/leak_suspects/index.html   # Linux

# リモートサーバーの場合 → ローカルにコピーしてブラウザで開く
scp -r user@server:leak_reports ./
```

### 方法B: CLI でテキスト表示

```bash
# HTML タグを除去してテキスト表示
cat leak_reports/leak_suspects/index.html | sed 's/<[^>]*>//g' | grep -v "^$"

# Problem Suspect を検索
grep -r "Problem Suspect" leak_reports/leak_suspects/

# 特定のクラス名を検索
grep -r "HashMap" leak_reports/leak_suspects/
```

---

# 3. レポートの解析方法

## 解析の順番

```
1. System Overview    ← 全体像を把握
       ↓
2. Leak Suspects      ← 犯人を特定
       ↓
3. Top Components     ← 詳細を深掘り（必要に応じて）
```

---

## Step 1: System Overview（全体像の把握）

**目的**: ヒープ全体の状態を理解する

### 見るべきセクション

| セクション | 何がわかる？ |
|-----------|-------------|
| **Heap Dump Overview** | ヒープサイズ、オブジェクト数、クラス数 |
| **Top Consumers** | どのクラスがメモリを多く使っているか |
| **Class Histogram** | クラス別のインスタンス数とサイズ |

### 確認ポイントと見る場所

| 確認ポイント | 見る場所 |
|-------------|---------|
| ヒープ全体のサイズは？ | **Heap Dump Overview** → Used heap dump |
| オブジェクト数は異常に多くないか？ | **Heap Dump Overview** → Number of objects |
| 特定のクラスが突出してメモリを使っていないか？ | **Top Consumers** → Biggest Objects |
| 予想外のクラスが上位にいないか？ | **Class Histogram** → 上位のクラス名 |

---

## Step 2: Leak Suspects（犯人の特定）

**目的**: リークの原因を特定する

### 見るべきセクション

| セクション | 何がわかる？ |
|-----------|-------------|
| **Overview（円グラフ）** | メモリ使用割合の視覚化 |
| **Problem Suspect 1, 2...** | リークの疑いがあるオブジェクト |
| **Shortest Paths** | GC Root からリークオブジェクトへの参照パス |
| **Accumulated Objects** | 蓄積されているオブジェクトの一覧 |

### 確認ポイントと見る場所

| 確認ポイント | 見る場所 |
|-------------|---------|
| 1つのオブジェクトが大部分を占めていないか？ | **Overview** の円グラフ |
| Problem Suspect の Description に何と書いてある？ | **Problem Suspect 1** → Description |
| どの変数がオブジェクトを保持している？ | **Shortest Paths To the Accumulation Point** |
| 何が大量に蓄積されている？ | **All Accumulated Objects by Class** |

### Problem Suspect の読み方

実際のレポート例:

```
The classloader/component sbt.internal.LayeredClassLoader @ 0xf9eb2138
occupies 83,781,232 (65.46%) bytes.
The memory is accumulated in one instance of scala.collection.mutable.HashMap$Node[],
loaded by sbt.internal.ScalaLibraryClassLoader @ 0xf9eb2280,
which occupies 74,746,784 (58.40%) bytes.
```

これを分解すると:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ sbt.internal.LayeredClassLoader @ 0xf9eb2138                            │
│ occupies 83,781,232 (65.46%) bytes                                      │
│                                                                         │
│ → あなたのアプリケーション全体で 83.78 MB（ヒープの65%）を使用           │
└─────────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────────┐
│ The memory is accumulated in one instance of                            │
│ scala.collection.mutable.HashMap$Node[]                                 │
│                                                                         │
│ → その中で、HashMap の内部配列（Node[]）1個にメモリが集中している        │
│   （HashMap$Node[] は mutable.HashMap の内部データ構造）                 │
└─────────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────────┐
│ which occupies 74,746,784 (58.40%) bytes                                │
│                                                                         │
│ → その HashMap だけで 74.75 MB（ヒープの58%）を占有！                    │
│   これが犯人                                                            │
└─────────────────────────────────────────────────────────────────────────┘
```

#### Keywords セクションの意味

| キーワード | 説明 |
|-----------|------|
| `sbt.internal.LayeredClassLoader` | あなたのアプリのクラスをロードした場所 |
| `scala.collection.mutable.HashMap$Node[]` | リークしているデータ構造 |
| `sbt.internal.ScalaLibraryClassLoader` | Scala標準ライブラリをロードした場所 |

---

### Shortest Paths の読み方

```
下から上へ読む（GC Root → リークオブジェクト）:

scala.collection.mutable.HashMap$Node[]    ← リークしているデータ
        ↑
scala.collection.mutable.HashMap
        ↑ heavyDataCache                   ← この変数名が重要！
class example.LeakyApp$                    ← アプリケーションのクラス
        ↑
sbt.internal.LayeredClassLoader            ← GC Root
```

---

### Shortest Paths vs Accumulated Objects in Dominator Tree

| ビュー | 目的 | 答える質問 |
|--------|------|-----------|
| **Shortest Paths** | 参照の「経路」を見る | 「**なぜ**GCされないのか？」 |
| **Dominator Tree** | メモリの「内訳」を見る | 「**何が**どれだけ溜まっているか？」 |

```
Shortest Paths を見るとき:
  → 「heavyDataCache という変数が原因だ」と特定

Dominator Tree を見るとき:
  → 「中身は 100KB の byte[] が 500個ある」と把握
```

| 状況 | 見るべきビュー |
|------|---------------|
| 「どの変数を直せばいい？」 | Shortest Paths |
| 「何が溜まってるの？」 | Dominator Tree |
| 「なぜ GC されない？」 | Shortest Paths |
| 「メモリの内訳は？」 | Dominator Tree |

---

## Step 3: Top Components（詳細調査）

**目的**: 大きなコンポーネントを深掘りする

### レポートの構造

```
Top Components レポート
│
├── sbt.internal.LayeredClassLoader (66%)  ← Component（クラスローダー単位）
│   ├── Overview                  ← 概要
│   ├── Details                   ← 詳細情報
│   ├── Top Consumers             ← 大きいオブジェクト
│   ├── Retained Set              ← 保持しているオブジェクト一覧
│   ├── Possible Memory Waste     ← 無駄なメモリ使用
│   │   ├── Duplicate Strings         ← 重複文字列
│   │   ├── Empty Collections         ← 空のコレクション
│   │   ├── Collection Fill Ratios    ← コレクションの充填率
│   │   ├── Zero-Length Arrays        ← 長さ0の配列
│   │   └── Array Fill Ratios         ← 配列の充填率
│   └── Miscellaneous             ← その他（参照統計など）
│
├── <system class loader> (14%)   ← 別の Component
└── ...
```

### 見るべきセクション

| セクション | 何がわかる？ |
|-----------|-------------|
| **Overview** | 各コンポーネントの概要（サイズ、割合） |
| **Top Consumers** | そのコンポーネント内で大きいオブジェクト |
| **Possible Memory Waste** | 無駄なメモリ使用（以下を含む） |
| └─ Duplicate Strings | 同じ文字列が複数存在していないか |
| └─ Empty Collections | 空のまま確保されているコレクション |
| └─ Collection Fill Ratios | コレクションの使用効率（充填率） |

### 確認ポイント

- [ ] どのクラスローダー/パッケージが大きいか？（Overview）
- [ ] コレクションに無駄がないか？（Collection Fill Ratios）
- [ ] 重複した文字列がないか？（Duplicate Strings）
- [ ] 空のコレクションが大量にないか？（Empty Collections）

---

## 解析フローチャート

```
┌─────────────────────────────────────────────────────────────┐
│  ヒープダンプ解析フロー                                      │
│                                                             │
│  1. System Overview → 「何が大きい？」を把握                 │
│          ↓                                                  │
│  2. Leak Suspects → 「誰が保持している？」を特定             │
│          ↓           Shortest Paths の変数名に注目！        │
│          ↓                                                  │
│  3. コードを確認 → 該当する変数を見つける                    │
│          ↓                                                  │
│  4. 修正方針を決める                                        │
│       - キャッシュに上限を設ける                            │
│       - 不要になったら削除する                              │
│       - WeakReference を使う                                │
└─────────────────────────────────────────────────────────────┘
```

---

# 4. 用語解説

## Shallow Heap vs Retained Heap

| 用語 | 意味 |
|------|------|
| **Shallow Heap** | オブジェクト自身のサイズ（フィールドのみ） |
| **Retained Heap** | このオブジェクトを解放したら空くメモリ量 |

```
例: HashMap
  Shallow Heap = 32 bytes（HashMap 自身）
  Retained Heap = 74 MB（中身のデータ全部）

→ HashMap を削除すれば 74MB が解放される
```

---

## GC Roots とは

**GC Root = ガベージコレクションの起点となるオブジェクト**

```
GC（ガベージコレクション）の仕組み:

1. GC Root から参照をたどる
2. たどれるオブジェクトは「生きている」
3. たどれないオブジェクトは「ゴミ」→ 解放

┌─────────────────────────────────────────────────────────────┐
│  GC Root（起点）                                            │
│    ├── オブジェクトA ─── オブジェクトB                       │
│    │                      └── オブジェクトC                 │
│    └── オブジェクトD                                        │
│                                                             │
│  オブジェクトE（どこからも参照されない）→ ゴミとして回収       │
└─────────────────────────────────────────────────────────────┘
```

### GC Root の種類

| 種類 | 説明 |
|------|------|
| **Static 変数** | クラスの static フィールド |
| **スレッドのスタック** | 実行中のメソッドのローカル変数 |
| **JNI 参照** | ネイティブコードからの参照 |
| **クラスローダー** | ロードされたクラスを保持 |

### なぜ重要か

```
メモリリーク = GC Root から参照がつながっているので解放されない

Shortest Paths で GC Root を見ることで:
  「なぜこのオブジェクトが解放されないのか」がわかる
```

---

## Number of objects / Number of classes の違い

| 用語 | 意味 |
|------|------|
| **Number of classes** | ロードされた「クラス定義」の数（.class ファイルの数） |
| **Number of objects** | インスタンス（オブジェクト）の数 |

```
例:
  class UserSession { ... }   ← これは 1 class

  new UserSession()           ← これは 1 object
  new UserSession()           ← これは 1 object
  new UserSession()           ← これは 1 object

  Number of classes: 1
  Number of objects: 3（UserSession のインスタンスが 3 個）
```

---

## オブジェクト数・クラス数の目安

**絶対的な基準はない。アプリケーションの規模による。**

### 相対的な判断基準

```
判断方法:

1. 同じアプリの「正常時」と比較する
   - 正常時: 10万オブジェクト
   - 異常時: 100万オブジェクト → 10倍に増えている！

2. 特定のクラスのインスタンス数に注目
   - UserSession が 10個 → 正常（ログイン中のユーザー数）
   - UserSession が 100万個 → 異常（セッションがリークしている）
```

---

## Class Loaders とは

**クラスローダー = .class ファイルを読み込んで JVM にロードする仕組み**

```
┌─────────────────────────────────────────────────────────────┐
│  JVM                                                        │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Bootstrap ClassLoader                               │   │
│  │  - java.lang.*, java.util.* などコア API             │   │
│  └─────────────────────────────────────────────────────┘   │
│          ↓                                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Platform ClassLoader (旧 Extension)                 │   │
│  │  - JDK の拡張ライブラリ                              │   │
│  └─────────────────────────────────────────────────────┘   │
│          ↓                                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Application ClassLoader                             │   │
│  │  - あなたのアプリケーションのクラス                   │   │
│  └─────────────────────────────────────────────────────┘   │
│          ↓                                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  sbt.internal.LayeredClassLoader                     │   │
│  │  - sbt が動的にロードするクラス（あなたのアプリ）     │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## Top Consumers に表示されるクラスの説明

sbt で実行した場合に表示される主なクラス：

| クラス名 | 説明 |
|----------|------|
| **sbt.internal.LayeredClassLoader** | sbt がアプリケーションのクラスをロードするクラスローダー。**あなたのアプリのオブジェクトはここに含まれる** |
| **scala.tools.nsc.classpath.ZipAndJarClassPath** | Scala コンパイラが JAR を読むためのオブジェクト。アプリとは無関係 |
| **sbt.internal.MetaBuildLoader** | sbt のビルド定義（build.sbt）を読み込むクラスローダー。アプリとは無関係 |
| **scala.internal.BuildStructure** | sbt のプロジェクト構造を表すオブジェクト。アプリとは無関係 |
| **java.util.zip.ZipFile$Source** | JAR ファイル（= ZIP 形式）を読むためのオブジェクト。アプリとは無関係 |

### Top Consumers の見方

```
┌─────────────────────────────────────────────────────────────┐
│  Top Consumers を見るとき:                                  │
│                                                             │
│  sbt.internal.LayeredClassLoader (60%)                     │
│    └── これがあなたのアプリケーション                        │
│        └── この中に heavyDataCache などがある               │
│                                                             │
│  その他（scala.tools.*, sbt.internal.*, java.util.zip.*）  │
│    └── sbt やコンパイラの動作に必要なもの                   │
│    └── 通常は無視してよい                                   │
│                                                             │
│  注目すべきは:                                              │
│    「あなたのパッケージ名（example.*）」が上位にあるか       │
│    「予想外に大きいオブジェクト」がないか                    │
└─────────────────────────────────────────────────────────────┘
```

---

# 5. よくあるリークパターンと修正方法

## パターン1: キャッシュの肥大化

```scala
// NG: 上限なしのキャッシュ
private val cache = mutable.Map[String, Data]()

// OK: サイズ上限付きキャッシュ（LRU）
import com.google.common.cache.CacheBuilder
private val cache = CacheBuilder.newBuilder()
  .maximumSize(1000)
  .expireAfterWrite(10, TimeUnit.MINUTES)
  .build[String, Data]()
```

## パターン2: コレクションへの追加のみ

```scala
// NG: 追加するだけで削除しない
private val logs = mutable.ArrayBuffer[Log]()
def addLog(log: Log): Unit = logs += log

// OK: サイズを制限する
private val logs = mutable.Queue[Log]()
def addLog(log: Log): Unit = {
  logs.enqueue(log)
  while (logs.size > 1000) logs.dequeue()
}
```

## パターン3: リスナーの登録解除忘れ

```scala
// NG: 登録だけして解除しない
eventBus.register(listener)

// OK: 不要になったら解除
eventBus.register(listener)
// ... 後で
eventBus.unregister(listener)
```

---

# 6. GUI版 MAT での解析手順

## 解析の全体フロー

```
┌─────────────────────────────────────────────────────────────┐
│  Step 1: Overview で全体像を把握                            │
│          ↓                                                  │
│  Step 2: Leak Suspects で犯人を特定                         │
│          ↓                                                  │
│  Step 3: Histogram でクラス別の状況を確認                   │
│          ↓                                                  │
│  Step 4: Dominator Tree でメモリ内訳を深掘り                │
│          ↓                                                  │
│  Step 5: Path To GC Roots で参照チェーンを確認              │
└─────────────────────────────────────────────────────────────┘
```

---

## Step 1: Overview（全体像の把握）

**場所**: ファイルを開いた直後の画面

**目的**: ヒープ全体の状況をざっと把握

**見るポイント**:

| 項目 | 確認すること |
|------|-------------|
| Size | ヒープ全体のサイズ |
| Classes | ロードされたクラス数 |
| Objects | オブジェクト数 |
| 円グラフ | 何がメモリを占めているか |

---

## Step 2: Leak Suspects（犯人の特定）

**場所**: Overview 画面の「Leak Suspects」リンク、または Reports メニュー

**目的**: リークの疑いがあるオブジェクトを自動検出

**見るポイント**:

| 項目 | 確認すること |
|------|-------------|
| Problem Suspect | どのオブジェクトが怪しいか |
| Description | 何が問題か（%、サイズ） |
| Shortest Paths | どの変数が保持しているか |
| Accumulated Objects | 何が蓄積されているか |

---

## Step 3: Histogram（クラス別の状況）

**場所**: ツールバーのアイコン、または Window > Histogram

**目的**: どのクラスのオブジェクトが多いか確認

### Histogram の見方

```
Class Name              | Objects | Shallow Heap | Retained Heap
────────────────────────────────────────────────────────────────
String                  | 50000   | 1.2 MB       | 1.5 MB
byte[]                  | 30000   | 800 KB       | 800 KB
HashMap$Node            | 500     | 16 KB        | >= 74 MB
```

### 注意点

| 表示 | 意味 |
|------|------|
| `>= 74 MB` | 「74MB 以上」の意味。参照の重複があるため正確な値ではない |
| Shallow Heap | オブジェクト自身のサイズ（実データ量を見るならこちら） |
| Retained Heap | 解放したら空くサイズ（参照先含む） |

### 操作のコツ

```
1. Retained Heap 列をクリックしてソート
   → 本当にメモリを消費しているクラスが上位に来る

2. 検索ボックスで自分のパッケージ名を検索（例: "example"）
   → アプリ固有のクラスだけ表示

3. Objects 数が異常に多いクラスに注目
   → リークしている可能性
```

### 右クリックメニュー

| 操作 | 目的 |
|------|------|
| List Objects > with outgoing references | このクラスが参照しているもの |
| List Objects > with incoming references | このクラスを参照しているもの |

---

## Step 4: Dominator Tree（メモリ内訳）

**場所**: ツールバーのアイコン、または Window > Dominator Tree

**目的**: 「誰が何を支配しているか」を階層的に見る

### Dominator Tree の見方

```
▼ sbt.internal.LayeredClassLoader (83 MB)
  ▼ example.LeakyApp$ (83 MB)
    ▼ heavyDataCache (HashMap) (74 MB)      ← ここが犯人
      ▼ HashMap$Node[] (74 MB)
        ├── Node → byte[100KB]
        ├── Node → byte[100KB]
        └── ... (500個)

→ heavyDataCache を解放すれば 74MB 空く
```

### Dominator（支配者）とは

```
オブジェクト A が B を「支配」する
= A を解放すると B も解放される
= GC Root から B への全てのパスが A を通る

例:
  HashMap を削除 → 中の Node[] も byte[] も全部解放
  → HashMap が Node[] と byte[] を「支配」している
```

---

## Step 5: Path To GC Roots（参照チェーン）

**場所**: オブジェクトを右クリック → Path To GC Roots → exclude weak references

**目的**: 「なぜ GC されないのか」を確認

### exclude weak references とは

```
Weak Reference（弱参照）:
  → GC を妨げない参照
  → リークの原因ではない
  → ノイズになるので除外する

exclude weak references を選ぶ:
  → 本当の原因（強参照）だけ表示
  → リークの真犯人が見つかる
```

### 参照チェーンの読み方

```
GC Root
  ↓
sbt.internal.LayeredClassLoader
  ↓
example.LeakyApp$
  ↓ heavyDataCache          ← この変数名をコードで探す！
scala.collection.mutable.HashMap
  ↓
HashMap$Node[]
  ↓
byte[]                      ← リークしているデータ
```

---

## 右クリックメニュー まとめ

| 操作 | 目的 | いつ使う？ |
|------|------|-----------|
| **List Objects > with outgoing references** | 参照先を見る | 「中身は何？」 |
| **List Objects > with incoming references** | 参照元を見る | 「誰が持ってる？」 |
| **Path To GC Roots > exclude weak references** | GC されない理由 | 「なぜ解放されない？」 |
| **Show Retained Set** | 解放されるオブジェクト一覧 | 「何 MB 空く？」 |
| **Java Basics > Open In Dominator Tree** | 支配ツリーで開く | 「内訳を見たい」 |

---

## 解析の流れ まとめ

| Step | 機能 | 質問に答える |
|------|------|-------------|
| 1 | Overview | 「ヒープはどんな状態？」 |
| 2 | Leak Suspects | 「何がリークしてる？」 |
| 3 | Histogram | 「どのクラスが多い？どのクラスが大きい？」 |
| 4 | Dominator Tree | 「誰が何を支配してる？何 MB 使ってる？」 |
| 5 | Path To GC Roots | 「なぜ GC されない？どの変数が原因？」 |

---

# 7. コマンドまとめ（コピペ用）

```bash
# === 1. ヒープダンプ取得（稼働中のプロセス） ===
PID=$(jps -l | grep "アプリ名" | awk '{print $1}')
jcmd $PID GC.heap_dump /tmp/heapdump.hprof

# === 2. レポート生成 ===
/opt/mat/ParseHeapDump.sh /tmp/heapdump.hprof \
  org.eclipse.mat.api:suspects \
  org.eclipse.mat.api:overview \
  org.eclipse.mat.api:top_components

# === 3. 解凍 ===
mkdir -p leak_reports
unzip -o /tmp/heapdump.hprof_Leak_Suspects.zip -d leak_reports/leak_suspects
unzip -o /tmp/heapdump.hprof_System_Overview.zip -d leak_reports/system_overview
unzip -o /tmp/heapdump.hprof_Top_Components.zip -d leak_reports/top_components

# === 4. 確認 ===
ls -la leak_reports/
# ブラウザで leak_reports/leak_suspects/index.html を開く
```
