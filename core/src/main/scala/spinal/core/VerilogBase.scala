/*
 * SpinalHDL
 * Copyright (c) Dolu, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */

package spinal.core

import scala.collection.mutable

/**
 * Created by PIC18F on 07.01.2015.
 */


trait VerilogBase extends VhdlVerilogBase{

  def emitSignal(ref: Node, typeNode: Node): String = {
    s"  ${emitDataType(typeNode)} ${emitReference(ref)};\n"
  }


  def emitClockEdge(clock: Bool, edgeKind: EdgeKind): String = {
    s"${
      edgeKind match {
        case RISING => "posedge"
        case FALLING => "negedge"
      }
    } ${emitReference(clock)}"
  }

  def emitResetEdge(reset: Bool, polarity: Polarity): String = {
    s"${
      polarity match {
        case HIGH => "posedge"
        case LOW => "negedge"
      }
    } ${emitReference(reset)}"
  }

  def emitEnumLiteral[T <: SpinalEnum](enum : SpinalEnumElement[T],encoding : SpinalEnumEncoding,prefix : String = "`") : String = {
    return prefix + enum.spinalEnum.getName() + "_" + encoding.getName() + "_" + enum.getName()
  }
  def emitEnumType[T <: SpinalEnum](enum : SpinalEnumCraft[T],prefix : String) : String = emitEnumType(enum.spinalEnum,enum.getEncoding,prefix)
  def emitEnumType(enum : SpinalEnum,encoding : SpinalEnumEncoding,prefix : String = "`") : String = {
    return prefix + enum.getName() + "_" + encoding.getName() + "_type"
  }

  def emitDataType(node: Node) = node match {
    case bool: Bool => ""
    case bitvector: BitVector =>  emitRange(bitvector)
    case enum: SpinalEnumCraft[_] => emitEnumType(enum,"`")
    case _ => throw new Exception("Unknown datatype"); ""
  }

  def emitDirection(baseType: BaseType) = baseType.dir match {
    case `in` => "input "
    case `out` => "output"
    case _ => throw new Exception("Unknown direction"); ""
  }


  def emitRange(node: Widthable) = s"[${node.getWidth - 1}:0]"




  var referenceSet : mutable.Set[Node with Nameable with ContextUser] = null
  def emitReference(node: Node): String = {
    node match {
      case n: Nameable with ContextUser => {
        if(referenceSet != null) referenceSet.add(n)
        n.getNameElseThrow
      }
    }
  }
  def emitAssignedReference(node: Node): String = {
    node match {
      case n: Nameable with ContextUser => {
        n.getNameElseThrow
      }
    }
  }
}
