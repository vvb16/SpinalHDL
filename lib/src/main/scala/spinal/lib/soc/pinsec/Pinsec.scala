package spinal.lib.soc.pinsec

import java.io.File
import java.nio.file.Files

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.ahblite._
import spinal.lib.bus.amba3.apb.{Apb3Interconnect, Apb3Gpio, Apb3Decoder, Apb3Config}
import spinal.lib.com.jtag.Jtag
import spinal.lib.cpu.riscv.impl.build.RiscvAhbLite3
import spinal.lib.cpu.riscv.impl.extension.{BarrelShifterFullExtension, DivExtension, MulExtension}
import spinal.lib.cpu.riscv.impl.{disable, dynamic, sync, CoreConfig}
import spinal.lib.io.TriStateArray

class Pinsec extends Component{
  val debug = true
  val interruptCount = 4
  val ahbConfig = AhbLite3Config(addressWidth = 32,dataWidth = 32)
  val apbConfig = Apb3Config(addressWidth = 16,dataWidth = 32)

  val io = new Bundle{
//    val ahbAccess = slave(AhbLite3(ahbConfig))
//    val jtag = slave(Jtag())
    val gpio = master(TriStateArray(32 bits))
    val interrupt = in Bits(interruptCount bits)
//    val debugResetIn  = if(debug) in Bool else null
    val debugResetOut = if(debug) out Bool else null
  }




  //replace wit null to disable instruction cache
  val iCacheConfig = null
  //         InstructionCacheConfig(
  //         cacheSize =4096,
  //         bytePerLine =32,
  //         wayCount = 1,
  //         wrappedMemAccess = true,
  //         addressWidth = 32,
  //         cpuDataWidth = 32,
  //         memDataWidth = 32
  //       )

  //replace wit null to disable data cache
  val dCacheConfig = null
  //         DataCacheConfig(
  //         cacheSize = 4096,
  //         bytePerLine =32,
  //         wayCount = 1,
  //         addressWidth = 32,
  //         cpuDataWidth = 32,
  //         memDataWidth = 32
  //       )
  //
  val coreConfig = CoreConfig(
    pcWidth = 32,
    addrWidth = 32,
    startAddress = 0x200,
    regFileReadyKind = sync,
    branchPrediction = disable,
    bypassExecute0 = false,
    bypassExecute1 = false,
    bypassWriteBack = false,
    bypassWriteBackBuffer = false,
    collapseBubble = false,
    fastFetchCmdPcCalculation = true,
    dynamicBranchPredictorCacheSizeLog2 = 7
  )

  coreConfig.add(new MulExtension)
  coreConfig.add(new DivExtension)
  coreConfig.add(new BarrelShifterFullExtension)
  //  p.add(new BarrelShifterLightExtension)


  val core      = new RiscvAhbLite3(coreConfig,iCacheConfig,dCacheConfig,debug,interruptCount,apbConfig)
//  val rom       = AhbLite3OnChipRam(ahbConfig,byteCount = 512 KB)
  val rom       = new AhbLite3OnChipRom(ahbConfig,{
    val bytes = Files.readAllBytes(new File("E:/vm/share/pinsec_test.bin").toPath()).map(v => BigInt(if(v < 0) v + 256 else v))
    val array =  (0 until bytes.length/4).map(i => B(bytes(i*4+0) + (bytes(i*4+1) << 8) + (bytes(i*4+2) << 16) + (bytes(i*4+3) << 24),32 bits))
    array
  })
  val ram       = AhbLite3OnChipRam(ahbConfig,byteCount = 512 KB)
  val gpioCtrl  = Apb3Gpio(apbConfig,32)
  
  val apbBridge = AhbLite3ToApb3Bridge(ahbConfig,apbConfig)
  val ahbInterconnect = AhbLite3InterconnectFactory(ahbConfig)
    .addSlaves(
      rom.io.ahb       -> (0x00000000L, 512 KB),
      ram.io.ahb       -> (0x04000000L, 512 KB),
      apbBridge.io.ahb -> (0xF0000000L,  64 KB)
    ).addConnections(
      core.io.i.toAhbLite3()
        -> List(rom.io.ahb, ram.io.ahb),
      core.io.d.toAhbLite3()
        -> List(rom.io.ahb, ram.io.ahb, apbBridge.io.ahb)
//      io.ahbAccess
//        -> List(rom.io.ahb, ram.io.ahb, apbBridge.io.ahb)
    ).build()

  val apbDecoder = Apb3Interconnect(
    master = apbBridge.io.apb,
    slaves = List(
      gpioCtrl.io.apb  -> (0x0000, 1 KB),
      core.io.debugBus -> (0xF000, 4 KB)
    )
  )

  if(interruptCount != 0) core.io.interrupt := io.interrupt
  if(debug){
    core.io.debugResetIn  <> ClockDomain.current.readResetWire//io.debugResetIn
    core.io.debugResetOut <> io.debugResetOut
  }
  gpioCtrl.io.gpio <> io.gpio
}


object Pinsec{
  def main(args: Array[String]) {
    SpinalConfig().dumpWave().generateVerilog(new Pinsec)
    SpinalConfig().dumpWave().generateVhdl(new Pinsec)
  }
}