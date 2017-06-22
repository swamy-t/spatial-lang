package spatial.lang
package control

import argon.core._
import forge._
import spatial.metadata._
import spatial.nodes._

// MemReduce and views
//   If view is staged, requires either direct access to its target via a def or its own load/store defs
//   If view is unstaged, requires unwrapping prior to use in result of Blocks / use as dependencies
//   However, if view is staged, have mutable sharing..

protected case class MemReduceAccum[T,C[T]](accum: C[T], style: ControlStyle, zero: Option[T], fold: scala.Boolean) {
  /** 1 dimensional memory reduction **/
  @api def apply(domain1D: Counter)(map: Index => C[T])(reduce: (T,T) => T)(implicit mem: Mem[T,C], mT: Type[T], bT: Bits[T], mC: Type[C[T]]): C[T] = {
    MemReduce.alloc(List(domain1D), accum, {x: List[Index] => map(x.head)}, reduce, style, zero, fold)
    accum
  }

  /** 2 dimensional memory reduction **/
  @api def apply(domain1: Counter, domain2: Counter)(map: (Index,Index) => C[T])(reduce: (T,T) => T)(implicit mem: Mem[T,C], mT: Type[T], bT: Bits[T], mC: Type[C[T]]): C[T] = {
    MemReduce.alloc(List(domain1,domain2), accum, {x: List[Index] => map(x(0),x(1)) }, reduce, style, zero, fold)
    accum
  }

  /** 3 dimensional memory reduction **/
  @api def apply(domain1: Counter, domain2: Counter, domain3: Counter)(map: (Index,Index,Index) => C[T])(reduce: (T,T) => T)(implicit mem: Mem[T,C], mT: Type[T], bT: Bits[T], mC: Type[C[T]]): C[T] = {
    MemReduce.alloc(List(domain1,domain2,domain3), accum, {x: List[Index] => map(x(0),x(1),x(2)) }, reduce, style, zero, fold)
    accum
  }

  /** N dimensional memory reduction **/
  @api def apply(domain1: Counter, domain2: Counter, domain3: Counter, domain4: Counter, domain5plus: Counter*)(map: List[Index] => C[T])(reduce: (T,T) => T)(implicit mem: Mem[T,C], mT: Type[T], bT: Bits[T], mC: Type[C[T]]): C[T] = {
    MemReduce.alloc(List(domain1,domain2,domain3,domain4) ++ domain5plus, accum, map, reduce, style, zero, fold)
    accum
  }
}

protected case class MemReduceClass(style: ControlStyle) {
  def apply[T,C[T]](accum: C[T]) = MemReduceAccum[T,C](accum, style, None, fold = false)
  def apply[T,C[T]](accum: C[T], zero: T) = MemReduceAccum[T,C](accum, style, Some(zero), fold = false)
}

protected case class MemFoldClass(style: ControlStyle) {
  def apply[T,C[T]](accum: C[T]) = MemReduceAccum[T,C](accum, style, None, fold = true)
  def apply[T,C[T]](accum: C[T], zero: T) = MemReduceAccum[T,C](accum, style, Some(zero), fold = true)
}


object MemFold   extends MemFoldClass(MetaPipe)
object MemReduce extends MemReduceClass(MetaPipe) {
  @internal def alloc[T:Type:Bits,C[T]](
    domain: Seq[Counter],
    accum:  C[T],
    map:    List[Index] => C[T],
    reduce: (T,T) => T,
    style:  ControlStyle,
    ident:  Option[T],
    fold:   Boolean
  )(implicit ctx: SrcCtx, mem: Mem[T,C], mC: Type[C[T]]): Controller = {
    val rV = (fresh[T], fresh[T])
    val itersMap = List.tabulate(domain.length){_ => fresh[Index] }

    val ctrsRed = mem.iterators(accum)
    val itersRed = ctrsRed.map{_ => fresh[Index] }

    val mBlk  = stageSealedBlock{ map(wrap(itersMap)).s }
    val rBlk  = stageColdLambda2(rV._1,rV._2){ reduce(wrap(rV._1), wrap(rV._2)).s }
    val ldResBlk = stageColdLambda1(mBlk.result){ mem.load(wrap(mBlk.result), wrap(itersRed), true).s }
    val ldAccBlk = stageColdLambda1(accum.s) { mem.load(accum, wrap(itersRed), true).s }
    val stAccBlk = stageColdLambda2(accum.s, rBlk.result){ mem.store(accum, wrap(itersRed), wrap(rBlk.result), true).s }

    val cchainMap = CounterChain(domain: _*)
    val cchainRed = CounterChain(ctrsRed: _*)
    val z = ident.map(_.s)

    val effects = mBlk.effects andAlso rBlk.effects andAlso ldResBlk.effects andAlso ldAccBlk.effects andAlso stAccBlk.effects
    val node = stageEffectful(OpMemReduce[T,C](Nil, cchainMap.s,cchainRed.s,accum.s,mBlk,ldResBlk,ldAccBlk,rBlk,stAccBlk,z,fold,rV,itersMap,itersRed), effects)(ctx)
    styleOf(node) = style
    levelOf(node) = OuterControl
    Controller(node)
  }

  @internal def op_mem_reduce[T:Type:Bits,C[T]](
    en:        Seq[Exp[Bit]],
    cchainMap: Exp[CounterChain],
    cchainRed: Exp[CounterChain],
    accum:     Exp[C[T]],
    map:       () => Exp[C[T]],
    loadRes:   Exp[C[T]] => Exp[T],
    loadAcc:   Exp[C[T]] => Exp[T],
    reduce:    (Exp[T], Exp[T]) => Exp[T],
    storeAcc:  (Exp[C[T]], Exp[T]) => Exp[MUnit],
    ident:     Option[Exp[T]],
    fold:      Boolean,
    rV:        (Bound[T], Bound[T]),
    itersMap:  Seq[Bound[Index]],
    itersRed:  Seq[Bound[Index]]
  )(implicit ctx: SrcCtx, mem: Mem[T,C], mC: Type[C[T]]): Sym[Controller] = {

    val mBlk = stageSealedBlock{ map() }
    val ldResBlk = stageColdLambda1(mBlk.result){ loadRes(mBlk.result) }
    val ldAccBlk = stageColdLambda1(accum){ loadAcc(accum) }
    val rBlk = stageColdLambda2(rV._1,rV._2){ reduce(rV._1,rV._2) }
    val stBlk = stageColdLambda2(accum, rBlk.result){ storeAcc(accum, rBlk.result) }

    val effects = mBlk.effects andAlso ldResBlk.effects andAlso ldAccBlk.effects andAlso rBlk.effects andAlso stBlk.effects
    stageEffectful( OpMemReduce[T,C](en, cchainMap, cchainRed, accum, mBlk, ldResBlk, ldAccBlk, rBlk, stBlk, ident, fold, rV, itersMap, itersRed), effects)(ctx)
  }
}