// See LICENSE.SiFive for license details.

package uec.teehardware.exampletop

import Chisel._
import chipyard._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.system._
import freechips.rocketchip.config._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.tile._
import testchipip._
import uec.teehardware.devices.sha3._
import uec.teehardware.devices.ed25519._
import uec.teehardware.devices.aes._
import uec.teehardware.devices.random._
import uec.teehardware.devices.usb11hs._

// Just like DefaultConfig for now, but with the peripherals
class TEEHWDefaultPeripherals extends Config((site, here, up) => {
  case PeripherySHA3Key =>
    SHA3Params(address = BigInt(0x10003000L))
  case Peripheryed25519Key =>
    ed25519Params(address = BigInt(0x10004000L))
  case PeripheryAESKey =>
    AESParams(address = BigInt(0x10007000L))
  case PeripheryUSB11HSKey =>
    USB11HSParams(address = BigInt(0x10008000L))
  case PeripheryRandomKey =>
    RandomParams(address = BigInt(0x10009000L))
  case BootROMParams =>
    BootROMParams(contentFileName = "./hardware/chipyard/generators/rocket-chip/bootrom/bootrom.img")
})

class WithAESAccel extends Config ((site, here, up) => {
  case BuildRoCC => Seq(
    (p: Parameters) => {
      val aes = LazyModule.apply(new AESROCC(OpcodeSet.custom0)(p))
      aes
    }
  )
})

class TEEHWDefaultConfig extends Config(
    new TEEHWDefaultPeripherals ++
    new WithAESAccel ++
    new WithNBigCores(1) ++
    new BaseConfig
)

class TEEHWJTAGConfig extends Config(
    new WithNBreakpoints(4) ++
    new WithJtagDTM ++
    new TEEHWDefaultConfig().alter((site,here,up) => {
      case DTSTimebase => BigInt(1000000)
      case JtagDTMKey => new JtagDTMConfig (
        idcodeVersion = 2,      // 1 was legacy (FE310-G000, Acai).
        idcodePartNum = 0x000,  // Decided to simplify.
        idcodeManufId = 0x489,  // As Assigned by JEDEC to SiFive. Only used in wrappers / test harnesses.
        debugIdleCycles = 5)    // Reasonable guess for synchronization
    })
)

/** Example Top with periphery devices and ports, and a Rocket subsystem */
class ExampleRocketSystemTEEHW(implicit p: Parameters) extends Subsystem
    with HasAsyncExtInterrupts
    with CanHaveMasterAXI4MemPort
    //with CanHaveMasterAXI4MMIOPort
    //with CanHaveSlaveAXI4Port
    with HasPeripherySHA3
    with HasPeripheryed25519
    with HasPeripheryAES
    with HasPeripheryRandom
    //with HasPeripheryUSB11HS
    with CanHavePeripherySerial
    with HasPeripheryBootROM {
  override lazy val module = new ExampleRocketSystemTEEHWModuleImp(this)
}

class ExampleRocketSystemTEEHWModuleImp[+L <: ExampleRocketSystemTEEHW](_outer: L)
  extends SubsystemModuleImp(_outer)
    with HasRTCModuleImp
    with HasExtInterruptsModuleImp
    with HasPeripherySHA3ModuleImp
    with HasPeripheryed25519ModuleImp
    with HasPeripheryAESModuleImp
    with HasPeripheryRandomModuleImp
    //with HasPeripheryUSB11HSModuleImp
    with CanHavePeripherySerialModuleImp
    with HasPeripheryBootROMModuleImp
    with DontTouch
