package spatial.lang

import argon.core._
import forge._
import spatial.nodes._
import spatial.files._

trait LUT[T] { this: Template[_] =>
  def s: Exp[LUT[T]]
}
object LUT {
  @api def apply[T:Type:Bits](dim: Int)(elems: T*): LUT1[T] = {
    checkDims(Seq(dim),elems)
    LUT1(alloc[T,LUT1](Seq(dim), unwrap(elems)))
  }
  @api def apply[T:Type:Bits](dim1: Int, dim2: Int)(elems: T*): LUT2[T] = {
    checkDims(Seq(dim1,dim2),elems)
    LUT2(alloc[T,LUT2](Seq(dim1,dim2), unwrap(elems)))
  }
  @api def apply[T:Type:Bits](dim1: Int, dim2: Int, dim3: Int)(elems: T*): LUT3[T] = {
    checkDims(Seq(dim1,dim2,dim3),elems)
    LUT3(alloc[T,LUT3](Seq(dim1,dim2,dim3), unwrap(elems)))
  }
  @api def apply[T:Type:Bits](dim1: Int, dim2: Int, dim3: Int, dim4: Int)(elems: T*): LUT4[T] = {
    checkDims(Seq(dim1,dim2,dim3,dim4),elems)
    LUT4(alloc[T,LUT4](Seq(dim1,dim2,dim3,dim4), unwrap(elems)))
  }
  @api def apply[T:Type:Bits](dim1: Int, dim2: Int, dim3: Int, dim4: Int, dim5: Int)(elems: T*): LUT5[T] = {
    checkDims(Seq(dim1,dim2,dim3,dim4,dim5),elems)
    LUT5(alloc[T,LUT5](Seq(dim1,dim2,dim3,dim4,dim5), unwrap(elems)))
  }

  @internal private def readData[T:Type:Bits](filename: String): Seq[T] = {
    try {
      loadCSV[T](filename, ",") { str => parseValue[T](str) }
    }
    catch {case e: Throwable =>
      error(ctx, c"Could not read file $filename to create LUT")
      error(ctx)
      throw e
    }
  }

  @api def fromFile[T:Type:Bits](dim1: Int)(filename: String): LUT1[T] = {
    LUT[T](dim1)(readData[T](filename):_*)
  }
  @api def fromFile[T:Type:Bits](dim1: Int, dim2: Int)(filename: String): LUT2[T] = {
    LUT[T](dim1, dim2)(readData[T](filename):_*)
  }
  @api def fromFile[T:Type:Bits](dim1: Int, dim2: Int, dim3: Int)(filename: String): LUT3[T] = {
    LUT[T](dim1, dim2, dim3)(readData[T](filename): _*)
  }
  @api def fromFile[T:Type:Bits](dim1: Int, dim2: Int, dim3: Int, dim4: Int)(filename: String): LUT4[T] = {
    LUT[T](dim1, dim2, dim3, dim4)(readData[T](filename):_*)
  }
  @api def fromFile[T:Type:Bits](dim1: Int, dim2: Int, dim3: Int, dim4: Int, dim5: Int)(filename: String): LUT5[T] = {
    LUT[T](dim1, dim2, dim3, dim4, dim5)(readData[T](filename):_*)
  }


  @internal def checkDims(dims: Seq[Int], elems: Seq[_]) = {
    if (dims.product != elems.length) {
      error(ctx, c"Specified dimensions of the LUT do not match the number of supplied elements (${dims.product} != ${elems.length})")
      error(ctx)
    }
  }
  @internal def flatIndexConst(indices: Seq[Int], dims: Seq[Int])(implicit ctx: SrcCtx): Int = {
    val strides = List.tabulate(dims.length){d => dims.drop(d+1).product }
    indices.zip(strides).map{case (a,b) => a*b }.sum
  }

  /** Constructors **/
  @internal def alloc[T:Type:Bits,C[_]<:LUT[_]](dims: Seq[Int], elems: Seq[Exp[T]])(implicit cT: Type[C[T]]) = {
    stageMutable(LUTNew[T,C](dims, elems))(ctx)
  }

  @internal def load[T:Type:Bits](lut: Exp[LUT[T]], inds: Seq[Exp[Index]], en: Exp[Bit]) = {
    def node = stage(LUTLoad(lut, inds, en))(ctx)

    if (inds.forall{case Const(c: BigDecimal) => true; case _ => false}) lut match {
      case Op(LUTNew(dims, elems)) =>
        val is = inds.map{case Const(c: BigDecimal) => c.toInt }
        val index = flatIndexConst(is, dims)
        if (index < 0 || index >= elems.length) {
          if (Globals.staging) {
            warn(ctx, s"Load from LUT at index " + is.mkString(", ") + " is out of bounds.")
            warn(ctx)
          }
          node
        }
        else elems(index)

      case _ => node
    }
    else node
  }
}

case class LUT1[T:Type:Bits](s: Exp[LUT1[T]]) extends Template[LUT1[T]] with LUT[T] {
  @api def apply(i: Index): T = wrap(LUT.load(s, Seq(i.s), Bit.const(true)))
}
object LUT1 {
  implicit def lut1Type[T:Type:Bits]: Type[LUT1[T]] = LUT1Type(typ[T])
}

case class LUT2[T:Type:Bits](s: Exp[LUT2[T]]) extends Template[LUT2[T]] with LUT[T] {
  @api def apply(r: Index, c: Index): T = wrap(LUT.load(s, Seq(r.s, c.s), Bit.const(true)))
}
object LUT2 {
  implicit def lut2Type[T:Type:Bits]: Type[LUT2[T]] = LUT2Type(typ[T])
}

case class LUT3[T:Type:Bits](s: Exp[LUT3[T]]) extends Template[LUT3[T]] with LUT[T] {
  @api def apply(r: Index, c: Index, p: Index): T = wrap(LUT.load(s, Seq(r.s, c.s, p.s), Bit.const(true)))
}
object LUT3 {
  implicit def lut3Type[T:Type:Bits]: Type[LUT3[T]] = LUT3Type(typ[T])
}

case class LUT4[T:Type:Bits](s: Exp[LUT4[T]]) extends Template[LUT4[T]] with LUT[T] {
  @api def apply(r: Index, c: Index, p: Index, q: Index): T = wrap(LUT.load(s, Seq(r.s, c.s, p.s, q.s), Bit.const(true)))
}
object LUT4 {
  implicit def lut4Type[T:Type:Bits]: Type[LUT4[T]] = LUT4Type(typ[T])
}

case class LUT5[T:Type:Bits](s: Exp[LUT5[T]]) extends Template[LUT5[T]] with LUT[T] {
  @api def apply(r: Index, c: Index, p: Index, q: Index, m: Index): T = wrap(LUT.load(s, Seq(r.s, c.s, p.s, q.s, m.s), Bit.const(true)))
}
object LUT5 {
  implicit def lut5Type[T:Type:Bits]: Type[LUT5[T]] = LUT5Type(typ[T])
}
