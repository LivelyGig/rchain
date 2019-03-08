package coop.rchain.comm.transport

import scala.concurrent.duration.Duration

import coop.rchain.comm._
import coop.rchain.crypto.codec.Base16
import coop.rchain.crypto.util.{CertificateHelper, CertificatePrinter}
import coop.rchain.shared.Log
import java.nio.file._

import coop.rchain.metrics.Metrics

import monix.catnap.MVar
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest._

class TcpTransportLayerSpec
    extends TransportLayerSpec[Task, TcpTlsEnvironment]
    with BeforeAndAfterEach {

  implicit val log: Log[Task]         = new Log.NOPLog[Task]
  implicit val scheduler: Scheduler   = Scheduler.Implicits.global
  implicit val metrics: Metrics[Task] = new Metrics.MetricsNOP

  var tempFolder: Path = null

  override def beforeEach(): Unit =
    tempFolder = Files.createTempDirectory("rchain")

  override def afterEach(): Unit =
    tempFolder.toFile.delete()

  def createEnvironment(port: Int): Task[TcpTlsEnvironment] =
    Task.delay {
      val host    = "127.0.0.1"
      val keyPair = CertificateHelper.generateKeyPair(true)
      val cert    = CertificatePrinter.print(CertificateHelper.generate(keyPair))
      val key     = CertificatePrinter.printPrivateKey(keyPair.getPrivate)
      val id      = CertificateHelper.publicAddress(keyPair.getPublic).map(Base16.encode).get
      val address = s"rnode://$id@$host?protocol=$port&discovery=0"
      val peer    = PeerNode.fromAddress(address).right.get
      TcpTlsEnvironment(host, port, cert, key, peer)
    }

  def maxMessageSize: Int = 4 * 1024 * 1024

  def createTransportLayer(
      env: TcpTlsEnvironment
  ): Task[(TransportLayer[Task], TransportLayerShutdown[Task])] =
    for {
      client <- CachedConnections[Task, TcpConnTag].map { implicit cache =>
                 new GrpcTransportClient(env.cert, env.key, 4 * 1024 * 1024, tempFolder, 100)
               }
      _ <- client.start()
    } yield (client, new TransportLayerShutdown(client.shutdown))

  def extract[A](fa: Task[A]): A = fa.runSyncUnsafe(Duration.Inf)

  def createDispatcherCallback: Task[DispatcherCallback[Task]] =
    MVar.empty[Task, Unit]().map(new DispatcherCallback(_))

  def createTransportLayerServer(env: TcpTlsEnvironment): Task[TransportLayerServer[Task]] =
    Task.delay {
      new GrpcTransportServer(env.port, env.cert, env.key, 4 * 1024 * 1024, tempFolder, 4)
    }
}

case class TcpTlsEnvironment(
    host: String,
    port: Int,
    cert: String,
    key: String,
    peer: PeerNode
) extends Environment
