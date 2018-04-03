package coop.rchain.rholang.interpreter

import coop.rchain.models.Channel.ChannelInstance.Quote
import coop.rchain.models.TaggedContinuation.TaggedCont.{Empty, ParBody, ScalaBodyRef}
import coop.rchain.models.{Channel, Par, TaggedContinuation}
import coop.rchain.rspace.IStore
import monix.eval.Task

trait Dispatch[M[_], A, K] {

  val reducer: Reduce[M]

  def dispatch(continuation: K, dataList: List[A]): M[Unit]
}

object Dispatch {

  // TODO: Make this function total
  def buildEnv(dataList: List[Seq[Channel]]): Env[Par] =
    Env.makeEnv(dataList.flatten.map({ case Channel(Quote(p)) => p }): _*)
}

class RholangOnlyDispatcher private (_reducer: => Reduce[Task])
    extends Dispatch[Task, Seq[Channel], TaggedContinuation] {

  val reducer: Reduce[Task] = _reducer

  def dispatch(continuation: TaggedContinuation, dataList: List[Seq[Channel]]): Task[Unit] =
    continuation.taggedCont match {
      case ParBody(par) =>
        val env = Dispatch.buildEnv(dataList)
        reducer.eval(par)(env)
      case ScalaBodyRef(_) =>
        Task.unit
      case Empty =>
        Task.unit
    }
}

object RholangOnlyDispatcher {

  def create(tuplespace: IStore[Channel, Seq[Channel], Seq[Channel], TaggedContinuation])
    : Dispatch[Task, Seq[Channel], TaggedContinuation] = {
    lazy val dispatcher: Dispatch[Task, Seq[Channel], TaggedContinuation] =
      new RholangOnlyDispatcher(reducer)
    lazy val reducer: Reduce[Task] =
      new Reduce.DebruijnInterpreter(tuplespace, dispatcher)
    dispatcher
  }
}

class RholangAndScalaDispatcher private (
    _reducer: => Reduce[Task],
    _dispatchTable: => Map[Long, Function1[List[Seq[Channel]], Task[Unit]]])
    extends Dispatch[Task, Seq[Channel], TaggedContinuation] {

  val reducer: Reduce[Task] = _reducer

  def dispatch(continuation: TaggedContinuation, dataList: List[Seq[Channel]]): Task[Unit] =
    continuation.taggedCont match {
      case ParBody(par) =>
        val env = Dispatch.buildEnv(dataList)
        reducer.eval(par)(env)
      case ScalaBodyRef(ref) =>
        _dispatchTable.get(ref) match {
          case Some(f) => f(dataList)
          case None    => Task.raiseError(new Exception(s"dispatch: no function for $ref"))
        }
      case Empty =>
        Task.unit
    }
}

object RholangAndScalaDispatcher {

  def create(tuplespace: IStore[Channel, Seq[Channel], Seq[Channel], TaggedContinuation],
             dispatchTable: => Map[Long, Function1[List[Seq[Channel]], Task[Unit]]])
    : Dispatch[Task, Seq[Channel], TaggedContinuation] = {
    lazy val dispatcher: Dispatch[Task, Seq[Channel], TaggedContinuation] =
      new RholangAndScalaDispatcher(reducer, dispatchTable)
    lazy val reducer: Reduce[Task] =
      new Reduce.DebruijnInterpreter(tuplespace, dispatcher)
    dispatcher
  }
}
