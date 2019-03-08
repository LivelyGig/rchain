package coop.rchain.casper.genesis.contracts
import coop.rchain.casper.helper.RhoSpec
import coop.rchain.rholang.build.CompiledRholangSource

import scala.concurrent.duration._

class NonNegativeNumberSpec
    extends RhoSpec(
      CompiledRholangSource("NonNegativeNumberTest.rho"),
      Seq(StandardDeploys.nonNegativeNumber),
      10.seconds
    )
