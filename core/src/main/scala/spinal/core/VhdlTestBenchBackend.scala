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

import java.nio.file.{Files, Paths}

import scala.StringBuilder
import scala.collection.mutable
import scala.collection.mutable.StringBuilder
import scala.io.Source

class VhdlTestBenchBackend(pc : PhaseContext) extends VhdlBase with PhaseMisc {
  import pc._

  override def useNodeConsumers: Boolean = false

  var out: java.io.FileWriter = null
  var tbName: String = null

  val userCodes = mutable.Map[String, String]()



  def outputFilePath = config.targetDirectory + "/" +  (if(pc.config.netlistFileName == null)(topLevel.definitionName + "_tb.vhd") else pc.config.netlistFileName.replace(".","_tb."))
  
  override def impl(pc : PhaseContext) : Unit = {
    if (tbName == null) tbName = topLevel.definitionName + "_tb"
    extractUserCodes

    val tbFile = new java.io.FileWriter(outputFilePath)
    tbFile.write(VhdlVerilogBase.getHeader("--",topLevel))
    val ret = new StringBuilder()


    emitLibrary(ret)
    emitUserCode("", "userLibrary", ret)
    ret ++= s"""
                |
                |entity $tbName is
                |end $tbName;
                |
                |architecture arch of $tbName is
                |""".stripMargin


    emitSignals(topLevel, ret)
    emitUserCode("  ", "userDeclarations", ret)

    ret ++= "begin\n"
    emitUserCode("  ", "userLogics", ret)
    emitComponentInstance(topLevel, ret)
    ret ++= "end arch;\n"


    tbFile.write(ret.result())
    tbFile.flush();
    tbFile.close();
  }



  def extractUserCodes: Unit = {
    if (!Files.exists(Paths.get(outputFilePath))) return
    val iterator = Source.fromFile(outputFilePath).getLines()
    val begin = "#spinalBegin"
    val end = "#spinalEnd"
    while (iterator.hasNext) {
      val line = iterator.next()
      if (line.contains(begin)) {
        val split = line.split(" ")
        val name = split.iterator.dropWhile(_ != begin).drop(1).next()
        var done = false
        val buffer = new StringBuilder()
        do {
          val line = iterator.next()
          if (line.contains(end))
            done = true
          else {
            buffer ++= line
            buffer ++= "\n"
          }
        } while (!done)
        userCodes += (name -> buffer.result())
      }
    }
  }

  def emitUserCode(tab: String, name: String, ret: StringBuilder): Unit = {
    ret ++= s"$tab-- #spinalBegin $name\n"
    if (userCodes.contains(name)) ret ++= userCodes(name)
    ret ++= s"$tab-- #spinalEnd $name\n"
  }

  def emitSignals(c: Component, ret: StringBuilder): Unit = {
    for (io <- c.getOrdredNodeIo) {
      val str = emitSignal(io, io)

      ret ++= {if(config.onlyStdLogicVectorAtTopLevelIo)
        str.replace("unsigned","std_logic_vector").replace("signed","std_logic_vector")
      else
        str}
    }
  }

  def emitComponentInstance(c: Component, ret: StringBuilder): Unit = {
    val definitionString = s"entity work.${c.definitionName}"
    ret ++= s"  uut : $definitionString\n"
    ret ++= s"    port map (\n"
    for (data <- c.getOrdredNodeIo) {
      ret ++= s"      ${emitReference(data)} =>  ${emitReference(data)},\n"
    }
    ret.setCharAt(ret.size - 2, ' ')

    ret ++= s"    );"
    ret ++= s"\n"

  }

}
