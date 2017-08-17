package org.ergoplatform.utils

import org.ergoplatform.mining.Miner
import org.ergoplatform.modifiers.ErgoFullBlock
import org.ergoplatform.modifiers.history.{ADProof, BlockTransactions, Header}
import org.ergoplatform.modifiers.mempool.AnyoneCanSpendTransaction
import org.ergoplatform.modifiers.mempool.proposition.{AnyoneCanSpendNoncedBox, AnyoneCanSpendProposition}
import org.ergoplatform.nodeView.history.ErgoSyncInfo
import org.ergoplatform.nodeView.state.{BoxHolder, UtxoState}
import org.ergoplatform.settings.Constants
import org.scalacheck.{Arbitrary, Gen}
import scorex.core.transaction.state.{BoxStateChanges, Insertion}
import scorex.testkit.CoreGenerators


trait ErgoGenerators extends CoreGenerators {

  val anyoneCanSpendProposition = AnyoneCanSpendProposition

  lazy val invalidAnyoneCanSpendTransactionGen: Gen[AnyoneCanSpendTransaction] = for {
    from: IndexedSeq[Long] <- smallInt.flatMap(i => Gen.listOfN(i + 1, positiveLongGen).map(_.toIndexedSeq))
    to: IndexedSeq[Long]   <- smallInt.flatMap(i => Gen.listOfN(i, positiveLongGen).map(_.toIndexedSeq))
  } yield AnyoneCanSpendTransaction(from, to)

  lazy val anyoneCanSpendBoxGen: Gen[AnyoneCanSpendNoncedBox] = for {
    nonce <- positiveLongGen
    value <- positiveLongGen
  } yield AnyoneCanSpendNoncedBox(nonce, value)

  lazy val boxesHolderGen: Gen[BoxHolder] = Gen.listOfN(20000, anyoneCanSpendBoxGen).map(l => BoxHolder(l))

  lazy val stateChangesGen: Gen[BoxStateChanges[AnyoneCanSpendProposition.type, AnyoneCanSpendNoncedBox]] = anyoneCanSpendBoxGen
    .map(b => BoxStateChanges[AnyoneCanSpendProposition.type, AnyoneCanSpendNoncedBox](Seq(Insertion(b))))

  lazy val ergoSyncInfoGen: Gen[ErgoSyncInfo] = for {
    answer <- Arbitrary.arbitrary[Boolean]
    idGenerator <- genBytesList(Constants.ModifierIdSize)
    ids <- Gen.nonEmptyListOf(idGenerator).map(_.take(ErgoSyncInfo.MaxBlockIds))
    fullBlockOpt <- Gen.option(idGenerator)
  } yield ErgoSyncInfo(answer, ids, fullBlockOpt)

  lazy val invalidHeaderGen: Gen[Header] = for {
    version <- Arbitrary.arbitrary[Byte]
    parentId <- genBytesList(Constants.ModifierIdSize)
    stateRoot <- genBytesList(Constants.ModifierIdSize)
    adRoot <- genBytesList(Constants.ModifierIdSize)
    transactionsRoot <- genBytesList(Constants.ModifierIdSize)
    nonce <- Arbitrary.arbitrary[Int]
    requiredDifficulty <- Arbitrary.arbitrary[Int]
    interlinks <- Gen.nonEmptyListOf(genBytesList(Constants.ModifierIdSize)).map(_.take(128))
    timestamp <- positiveLongGen
    extensionHash <- genBytesList(Constants.ModifierIdSize)
    votes <- genBytesList(5)
  } yield Header(version, parentId, interlinks, adRoot, stateRoot, transactionsRoot, timestamp, nonce,
    requiredDifficulty, extensionHash, votes)


  def validTransactions(boxHolder: BoxHolder): (Seq[AnyoneCanSpendTransaction], BoxHolder) = {
    val num = 10

    val spentBoxesCounts = (1 to num).map(_ => scala.util.Random.nextInt(10) + 1)

    val (boxes, bs) = boxHolder.take(spentBoxesCounts.sum)

    val (_, txs) = spentBoxesCounts.foldLeft((boxes, Seq[AnyoneCanSpendTransaction]())){case ((bxs, ts), fromBoxes) =>
      val (bxsFrom, remainder) = bxs.splitAt(fromBoxes)
      val newBoxes = bxsFrom.map(_.value)
      val tx = new AnyoneCanSpendTransaction(bxsFrom.map(_.nonce).toIndexedSeq, newBoxes.toIndexedSeq)
      (remainder, tx +: ts)
    }
    txs -> bs
  }

  def validFullBlock(parent: Header, utxoState: UtxoState, boxHolder: BoxHolder): ErgoFullBlock = {
    //todo: return updHolder
    val (transactions, updHolder) = validTransactions(boxHolder)

    val (adProofBytes, updStateDigest) = utxoState.proofsForTransactions(transactions).get

    val txsRoot = BlockTransactions.rootHash(transactions.map(_.id))

    val adProofhash = ADProof.proofDigest(adProofBytes)

    val time = System.currentTimeMillis()

    val fakeExtensionHash = Array.fill(32)(0.toByte)

    val header = Miner.genHeader(Constants.InitialDifficulty, parent, updStateDigest, adProofhash, txsRoot,
      fakeExtensionHash, Array.fill(5)(0.toByte), time)

    val blockTransactions = BlockTransactions(header.id, transactions)
    val adProof = ADProof(header.id, adProofBytes)

    ErgoFullBlock(header, blockTransactions, Some(adProof), None)
  }

  lazy val invalidBlockTransactionsGen: Gen[BlockTransactions] = for {
    headerId <- genBytesList(Constants.ModifierIdSize)
    txs <- Gen.nonEmptyListOf(invalidAnyoneCanSpendTransactionGen)
  } yield BlockTransactions(headerId, txs)

  lazy val randomADProofsGen: Gen[ADProof] = for {
    headerId <- genBytesList(Constants.ModifierIdSize)
    proof <- genBoundedBytes(32, 32 * 1024)
  } yield ADProof(headerId, proof)

  lazy val invalidErgoFullBlockGen: Gen[ErgoFullBlock] = for {
    header <- invalidHeaderGen
    txs <- invalidBlockTransactionsGen
    proof <- randomADProofsGen
  } yield ErgoFullBlock(header, txs, Some(proof), None)
}