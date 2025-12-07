package example

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

/**
 * 意図的にメモリリークを含むデモアプリケーション
 *
 * リークポイント:
 * 1. UserSession がクリアされずに蓄積
 * 2. RequestLog が無限に蓄積
 * 3. 巨大なキャッシュデータが保持され続ける
 */
object LeakyApp {

  // リークポイント1: セッションキャッシュ（クリアされない）
  private val sessionCache = mutable.Map[String, UserSession]()

  // リークポイント2: リクエストログ（無限に蓄積）
  private val requestLogs = mutable.ArrayBuffer[RequestLog]()

  // リークポイント3: 重いデータのキャッシュ
  private val heavyDataCache = mutable.Map[String, HeavyData]()

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Nothing] =
      ActorSystem(Behaviors.empty, "heap-demo")
    implicit val ec: ExecutionContextExecutor = system.executionContext

    val route = concat(
      // ユーザーセッション作成（リークする）
      path("login" / Segment) { userId =>
        get {
          val session = UserSession(
            id = java.util.UUID.randomUUID().toString,
            userId = userId,
            data = Map(
              "preferences" -> ("x" * 10000),  // 10KB のデータ
              "history" -> ("y" * 10000)
            ),
            createdAt = System.currentTimeMillis()
          )
          sessionCache.put(session.id, session)
          complete(s"Session created: ${session.id}, Total sessions: ${sessionCache.size}")
        }
      },

      // リクエストログ記録（リークする）
      path("api" / Segment) { endpoint =>
        get {
          val log = RequestLog(
            endpoint = endpoint,
            timestamp = System.currentTimeMillis(),
            payload = "z" * 5000  // 5KB のペイロード
          )
          requestLogs += log
          complete(s"OK - Total logs: ${requestLogs.size}")
        }
      },

      // 重いデータ取得（キャッシュがリークする）
      path("data" / Segment) { key =>
        get {
          val data = heavyDataCache.getOrElseUpdate(key, {
            HeavyData(
              key = key,
              content = new Array[Byte](100 * 1024),  // 100KB
              metadata = (1 to 1000).map(i => s"meta-$i" -> s"value-$i").toMap
            )
          })
          complete(s"Data for $key, Cache size: ${heavyDataCache.size}")
        }
      },

      // 現在のメモリ使用量を確認
      path("memory") {
        get {
          val runtime = Runtime.getRuntime
          val used = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
          val total = runtime.totalMemory() / 1024 / 1024
          val max = runtime.maxMemory() / 1024 / 1024
          complete(
            s"""Memory Usage:
               |  Used: ${used} MB
               |  Total: ${total} MB
               |  Max: ${max} MB
               |  Sessions: ${sessionCache.size}
               |  Logs: ${requestLogs.size}
               |  HeavyData: ${heavyDataCache.size}
               |""".stripMargin
          )
        }
      },

      // ヒープダンプ取得エンドポイント（デモ用）
      path("heapdump") {
        get {
          import java.lang.management.ManagementFactory
          import com.sun.management.HotSpotDiagnosticMXBean

          val server = ManagementFactory.getPlatformMBeanServer
          val bean = ManagementFactory.newPlatformMXBeanProxy(
            server,
            "com.sun.management:type=HotSpotDiagnostic",
            classOf[HotSpotDiagnosticMXBean]
          )
          val filename = s"/tmp/heapdump-${System.currentTimeMillis()}.hprof"
          bean.dumpHeap(filename, true)
          complete(s"Heap dump written to: $filename")
        }
      }
    )

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    bindingFuture.onComplete {
      case Success(binding) =>
        println(s"""
          |===========================================
          | Leaky App Started at ${binding.localAddress}
          |===========================================
          | Endpoints:
          |   GET /login/{userId}  - Create session (LEAKS!)
          |   GET /api/{endpoint}  - Log request (LEAKS!)
          |   GET /data/{key}      - Get heavy data (LEAKS!)
          |   GET /memory          - Check memory usage
          |   GET /heapdump        - Take heap dump
          |===========================================
          |""".stripMargin)
      case Failure(ex) =>
        println(s"Failed to bind: ${ex.getMessage}")
        system.terminate()
    }
  }
}

// データクラス
case class UserSession(
  id: String,
  userId: String,
  data: Map[String, String],
  createdAt: Long
)

case class RequestLog(
  endpoint: String,
  timestamp: Long,
  payload: String
)

case class HeavyData(
  key: String,
  content: Array[Byte],
  metadata: Map[String, String]
)
