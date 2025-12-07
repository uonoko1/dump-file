#!/bin/bash

# ===========================================
# メモリリークを発生させる負荷テストスクリプト
# ===========================================

BASE_URL="http://127.0.0.1:8080"
LOG_FILE="load-test-$(date +%Y%m%d-%H%M%S).log"

# ログ出力関数
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

# メモリ使用量を取得して表示
check_memory() {
    log "--- メモリ状況 ---"
    curl -s "$BASE_URL/memory" | tee -a "$LOG_FILE"
    echo "" | tee -a "$LOG_FILE"
}

# ヘッダー出力
log "=========================================="
log "負荷テスト開始"
log "=========================================="

# 初期メモリ状態を確認
log "【初期状態】"
check_memory

# -------------------------------------------
# フェーズ1: セッション作成（/login エンドポイント）
# 各セッション約 20KB のメモリを消費
# -------------------------------------------
log "【フェーズ1】セッション作成開始（各約20KB）"

for i in $(seq 1 100); do
    response=$(curl -s "$BASE_URL/login/user-$i")
    if [ $((i % 20)) -eq 0 ]; then
        log "  セッション作成: $i 件完了"
        check_memory
    fi
done

log "フェーズ1完了: 100セッション作成"
check_memory

# -------------------------------------------
# フェーズ2: リクエストログ蓄積（/api エンドポイント）
# 各リクエスト約 5KB のメモリを消費
# -------------------------------------------
log "【フェーズ2】リクエストログ蓄積開始（各約5KB）"

for i in $(seq 1 200); do
    response=$(curl -s "$BASE_URL/api/endpoint-$i")
    if [ $((i % 50)) -eq 0 ]; then
        log "  リクエストログ: $i 件完了"
        check_memory
    fi
done

log "フェーズ2完了: 200リクエストログ"
check_memory

# -------------------------------------------
# フェーズ3: 重いデータキャッシュ（/data エンドポイント）
# 各データ約 100KB のメモリを消費
# -------------------------------------------
log "【フェーズ3】重いデータキャッシュ開始（各約100KB）"

for i in $(seq 1 100); do
    response=$(curl -s "$BASE_URL/data/heavy-data-$i")
    if [ $((i % 10)) -eq 0 ]; then
        log "  重いデータ: $i 件完了"
        check_memory
    fi
done

log "フェーズ3完了: 100件の重いデータ"
check_memory

# -------------------------------------------
# フェーズ4: OOM まで追加負荷
# -------------------------------------------
log "【フェーズ4】OOM発生まで負荷継続"

count=0
while true; do
    count=$((count + 1))

    # 3種類のエンドポイントにランダムにリクエスト
    curl -s "$BASE_URL/login/extra-user-$count" > /dev/null 2>&1
    curl -s "$BASE_URL/api/extra-endpoint-$count" > /dev/null 2>&1
    curl -s "$BASE_URL/data/extra-data-$count" > /dev/null 2>&1

    if [ $((count % 20)) -eq 0 ]; then
        log "  追加負荷: $count ループ完了"

        # メモリ確認（失敗したら OOM の可能性）
        memory_response=$(curl -s --max-time 5 "$BASE_URL/memory" 2>&1)
        if [ $? -ne 0 ]; then
            log "!!! サーバー応答なし - OOM 発生の可能性 !!!"
            break
        fi
        echo "$memory_response" | tee -a "$LOG_FILE"
        echo "" | tee -a "$LOG_FILE"
    fi

    # 無限ループ防止（500回で停止）
    if [ $count -ge 500 ]; then
        log "最大ループ数に到達（500回）"
        break
    fi
done

log "=========================================="
log "負荷テスト終了"
log "ログファイル: $LOG_FILE"
log "=========================================="
