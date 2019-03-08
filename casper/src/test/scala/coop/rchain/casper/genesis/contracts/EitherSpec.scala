package coop.rchain.casper.genesis.contracts
import coop.rchain.casper.helper.RhoSpec
import coop.rchain.rholang.build.CompiledRholangSource

import scala.concurrent.duration._

class EitherSpec
    extends RhoSpec(
      CompiledRholangSource("EitherTest.rho"),
      Seq(StandardDeploys.either),
      10.seconds
    )
