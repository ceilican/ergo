package org.ergoplatform.nodeView.state

import org.ergoplatform.modifiers.{ErgoFullBlock, ErgoPersistentModifier}
import scorex.core.transaction.state.MinimalState.VersionTag
import ErgoState.Digest
import scorex.core.utils.ScorexLogging

import scala.util.{Failure, Success, Try}

/**
  * Minimal state variant which is storing only digest of UTXO authenticated as a dynamic dictionary.
  * See https://eprint.iacr.org/2016/994 for details on this mode.
  */
class DigestState extends ErgoState[DigestState] with ScorexLogging {
  //todo: persistence for rootHash?
  override lazy val rootHash: Digest = ???

  override def version: VersionTag = ???

  override def validate(mod: ErgoPersistentModifier): Try[Unit] = mod match {
    case fb: ErgoFullBlock =>
      val txs = fb.blockTransactions.txs
      val declaredHash = fb.header.ADProofsRoot

      txs.foldLeft(Success(): Try[Unit]) { case (status, tx) =>
        status.flatMap(_ => tx.semanticValidity)
      }.flatMap(_ => fb.aDProofs.map(_.verify(operations(txs), ???, declaredHash)) //todo: prev hash
        .getOrElse(Failure(new Error("Proofs are empty"))))

    case a: Any => log.info(s"Modifier not validated: $a"); Try(this)
  }

  override def applyModifier(mod: ErgoPersistentModifier): Try[DigestState] = mod match {
    case fb: ErgoFullBlock =>
      validate(fb).map(_ => new DigestState{
        override lazy val rootHash = fb.header.ADProofsRoot
      })
    case a: Any => log.info(s"Unhandled modifier: $a"); Try(this)
  }

  override def rollbackTo(version: VersionTag): Try[DigestState] = ???
}