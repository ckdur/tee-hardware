package uec.teehardware

import chisel3._
import chisel3.util.log2Up
import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tile._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._
import uec.teehardware.devices.aes._
import uec.teehardware.devices.ed25519._
import uec.teehardware.devices.sha3._
import uec.teehardware.devices.usb11hs._
import uec.teehardware.devices.random._
import uec.teehardware.devices.opentitan.aes._
import uec.teehardware.devices.opentitan.alert._
import uec.teehardware.devices.opentitan.hmac._
import uec.teehardware.devices.opentitan.otp_ctrl._
import boom.common._
import uec.teehardware.devices.opentitan.nmi_gen._
import uec.teehardware.ibex._

// ***************** ISA Configs (ISACONF) ********************
class RV64GC extends Config((site, here, up) => {
  case XLen => 64
})

class RV64IMAC extends Config((site, here, up) => {
  case XLen => 64
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(core = r.core.copy(fpu = None))
  }
  case BoomTilesKey => up(BoomTilesKey, site) map { r =>
    r.copy(core = r.core.copy(fpu = None,
      issueParams = r.core.issueParams.filter(_.iqType != IQT_FP.litValue)))
  }
})

// ************ Hybrid core configurations (HYBRID) **************

//Only Rocket: 2 cores
class Rocket extends Config(
  new WithNBigCores(2) ++
    new chipyard.config.WithL2TLBs(entries = 1024) ++
    new freechips.rocketchip.subsystem.WithInclusiveCache(capacityKB = 512))
class RocketReduced extends Config(
  new WithSmallCacheBigCore(2) ++
    new chipyard.config.WithL2TLBs(entries = 256) ++ // use L2 TLBs
    new freechips.rocketchip.subsystem.WithInclusiveCache(capacityKB = 128) )

// Ibex only (For microcontrollers)
class Ibex extends Config(
  new WithNIbexCores(1) ++
    new freechips.rocketchip.subsystem.WithInclusiveCache(capacityKB = 1))

// Rocket Micro (For microcontrollers)
class RocketMicro extends Config(
  new WithNSmallCores(1).alter((site, here, up) => {
    case RocketTilesKey => up(RocketTilesKey, site) map { r =>
      r.copy(
        btb = None,
        dcache = r.dcache map {d =>
          d.copy(
            nSets = 256, // 16Kb scratchpad
            nWays = 1,
            nTLBEntries = 4,
            nMSHRs = 0
            //scratch = Some(0x80000000L) // TODO: Not possible to put the scratchpad here
          )
        },
        icache = r.icache map {i =>
          i.copy(
            nSets = 64,
            nWays = 1,
            nTLBEntries = 4
          )
        }
      )
    }
  }) ++
    new chipyard.config.WithL2TLBs(entries = 4) ++ // use L2 TLBs
    new freechips.rocketchip.subsystem.WithInclusiveCache(capacityKB = 1)
)

// Non-secure Ibex (Without Isolation)
class Ibex2RocketNonSecure extends Config(
  new WithRenumberHartsWithIbex(rocketFirst = true) ++ //Rocket first, Ibex last
    new WithNBigCores(2) ++
    new WithNIbexCores(1) ++
    new chipyard.config.WithL2TLBs(entries = 1024) ++  // use L2 TLBs
    new freechips.rocketchip.subsystem.WithInclusiveCache(capacityKB = 512))

// Non-secure Ibex (Without Isolation) but reduced
class Ibex2RocketNonSecureReduced extends Config(
  new WithRenumberHartsWithIbex(rocketFirst = true) ++ //Rocket first, Ibex last
    new WithSmallCacheBigCore(2) ++
    new WithNIbexCores(1) ++
    new chipyard.config.WithL2TLBs(entries = 256) ++ // use L2 TLBs
    new freechips.rocketchip.subsystem.WithInclusiveCache(capacityKB = 128))

// ************ BootROM configuration (BOOTSRC) **************
class BOOTROM extends Config((site, here, up) => {
  case PeripheryMaskROMKey => List(
    MaskROMParams(address = BigInt(0x20000000), depth = 4096, name = "BootROM"))
  case TEEHWResetVector => 0x20000000
  case PeripherySPIFlashKey => List() // disable SPIFlash
})

class QSPI extends Config((site, here, up) => {
  case PeripheryMaskROMKey => List( //move BootROM back to 0x10000
    MaskROMParams(address = 0x10000, depth = 4096, name = "BootROM")) //smallest allowed depth is 16
  case TEEHWResetVector => 0x10040
  case PeripherySPIFlashKey => List(
    SPIFlashParams(fAddress = 0x20000000, rAddress = 0x64005000, defaultSampleDel = 3))
})

// ************ Chip Peripherals (PERIPHERALS) ************
class TEEHWPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(
    UARTParams(address = BigInt(0x64000000L)))
  case PeripherySPIKey => List(
    SPIParams(rAddress = BigInt(0x64001000L)))
  case PeripheryGPIOKey => List(
    GPIOParams(address = BigInt(0x64002000L), width = 16))
  case GPIOInKey => 8
  // TEEHW devices
  case PeripherySHA3Key => List(
    SHA3Params(address = BigInt(0x64003000L)))
  case Peripheryed25519Key => List(
    ed25519Params(address = BigInt(0x64004000L)))
  case PeripheryI2CKey => List(
    I2CParams(address = 0x64006000))
  case PeripheryAESKey => List(
    AESParams(address = BigInt(0x64007000L)))
  case PeripheryUSB11HSKey => List(
    USB11HSParams(address = BigInt(0x64008000L)))
  case PeripheryRandomKey => List(
    RandomParams(address = BigInt(0x64009000L), impl = 1),
    RandomParams(address = BigInt(0x6400A000L), impl = 0))
  // OpenTitan devices
  case PeripheryAESOTKey => List()
  case PeripheryHMACKey => List()
  case PeripheryOTPCtrlKey => List()
  case PeripheryAlertKey =>
    AlertParams(address = BigInt(0x64100000L))
  case PeripheryNmiGenKey =>
    NmiGenParams(address = BigInt(0x64200000L))
})

class OpenTitanPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(
    UARTParams(address = BigInt(0x64000000L)))
  case PeripherySPIKey => List(
    SPIParams(rAddress = BigInt(0x64001000L)))
  case PeripheryGPIOKey => List(
    GPIOParams(address = BigInt(0x64002000L), width = 16))
  case GPIOInKey => 8
  // TEEHW devices
  case PeripherySHA3Key => List()
  case Peripheryed25519Key => List()
  case PeripheryI2CKey => List()
  case PeripheryAESKey => List()
  case PeripheryUSB11HSKey => List()
  case PeripheryRandomKey => List()
  // OpenTitan devices
  case PeripheryAESOTKey => List(
    AESOTParams(address = BigInt(0x6400A000L)))
  case PeripheryHMACKey => List(
    HMACParams(address = BigInt(0x6400B000L)))
  case PeripheryOTPCtrlKey => List(
    OTPCtrlParams(address = BigInt(0x6400C000L)))
  case PeripheryAlertKey =>
    AlertParams(address = BigInt(0x64100000L))
  case PeripheryNmiGenKey =>
    NmiGenParams(address = BigInt(0x64200000L))
})

class TEEHWAndOpenTitanPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(
    UARTParams(address = BigInt(0x64000000L)))
  case PeripherySPIKey => List(
    SPIParams(rAddress = BigInt(0x64001000L)))
  case PeripheryGPIOKey => List(
    GPIOParams(address = BigInt(0x64002000L), width = 16))
  case GPIOInKey => 8
  // TEEHW devices
  case PeripherySHA3Key => List(
    SHA3Params(address = BigInt(0x64003000L)))
  case Peripheryed25519Key => List(
    ed25519Params(address = BigInt(0x64004000L)))
  case PeripheryI2CKey => List(
    I2CParams(address = 0x64006000))
  case PeripheryAESKey => List(
    AESParams(address = BigInt(0x64007000L)))
  case PeripheryUSB11HSKey => List(
    USB11HSParams(address = BigInt(0x64008000L)))
  case PeripheryRandomKey => List(
    RandomParams(address = BigInt(0x64009000L), impl = 1),
    RandomParams(address = BigInt(0x6400A000L), impl = 0))
  // OpenTitan devices
  case PeripheryAESOTKey => List(
    AESOTParams(address = BigInt(0x6400A000L)))
  case PeripheryHMACKey => List(
    HMACParams(address = BigInt(0x6400B000L)))
  case PeripheryOTPCtrlKey => List(
    OTPCtrlParams(address = BigInt(0x6400C000L)))
  case PeripheryAlertKey =>
    AlertParams(address = BigInt(0x64100000L))
  case PeripheryNmiGenKey =>
    NmiGenParams(address = BigInt(0x64200000L))
})

class NoSecurityPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(
    UARTParams(address = BigInt(0x64000000L)))
  case PeripherySPIKey => List(
    SPIParams(rAddress = BigInt(0x64001000L)))
  case PeripheryGPIOKey => List(
    GPIOParams(address = BigInt(0x64002000L), width = 16))
  case GPIOInKey => 8
  case PeripherySHA3Key => List()
  case Peripheryed25519Key => List()
  case PeripheryI2CKey => List()
  case PeripheryAESKey => List()
  case PeripheryUSB11HSKey => List()
  case PeripheryRandomKey => List()
  case PeripheryAESOTKey => List()
  case PeripheryHMACKey => List()
  case PeripheryAlertKey =>
    AlertParams(address = BigInt(0x64100000L))
  case PeripheryNmiGenKey =>
    NmiGenParams(address = BigInt(0x64200000L))
})

// *************** Bus configuration (MBUS) ******************

class MBus32 extends Config((site, here, up) => {
  case ExtMem => Some(MemoryPortParams(MasterPortParams(
    base = x"0_8000_0000",
    size = x"0_4000_0000",
    beatBytes = 4,
    idBits = 4), 1))
})

class MBus64 extends Config((site, here, up) => {
  case ExtMem => Some(MemoryPortParams(MasterPortParams(
    base = x"0_8000_0000",
    size = x"0_4000_0000",
    beatBytes = 8,
    idBits = 4), 1))
})

class MBusNone extends Config((site, here, up) => {
  case ExtMem => None
})

// *************** PCI Configuration (PCIE) ******************
class WPCIe extends Config((site, here, up) => {
  case IncludePCIe => true
})

class WoPCIe extends Config((site, here, up) => {
  case IncludePCIe => false
})

// *************** DDR Clock configurations (DDRCLK) ******************
class WSepaDDRClk extends Config((site, here, up) => {
  case DDRPortOther => true
})

class WoSepaDDRClk extends Config((site, here, up) => {
  case DDRPortOther => false
})

// *************** Board Config (BOARD) ***************
class DE4Config extends Config(
  new ChipConfig().alter((site,here,up) => {
    case FreqKeyMHz => 100.0
    /* DE4 is not support PCIe (yet) */
    case IncludePCIe => false
    case PeripheryRandomKey => up(PeripheryRandomKey, site) map {r =>
      r.copy(board = "Altera")
    }
  })
)

class TR4Config extends Config(
  new ChipConfig().alter((site,here,up) => {
    case FreqKeyMHz => 100.0
    /* TR4 is not support PCIe (yet) */
    case IncludePCIe => false
    case PeripheryRandomKey => up(PeripheryRandomKey, site) map {r =>
      r.copy(board = "Altera")
    }
  })
)

class VC707Config extends Config(
  new ChipConfig().alter((site,here,up) => {
    case FreqKeyMHz => 80.0
    /* Force to use BootROM because VC707 doesn't have enough GPIOs for QSPI */
    case PeripheryMaskROMKey => List(
      MaskROMParams(address = BigInt(0x20000000), depth = 4096, name = "BootROM"))
    case TEEHWResetVector => 0x20000000
    case PeripherySPIFlashKey => List() // disable SPIFlash
    /* Force to disable USB1.1, because there are no pins */
    case PeripheryUSB11HSKey => List()
    case PeripheryRandomKey => up(PeripheryRandomKey, site) map {r =>
      r.copy(board = "Xilinx")
    }
  })
)

class VC707MiniConfig extends Config(
  new MicroConfig().alter((site,here,up) => {
    case FreqKeyMHz => 80.0
    /* Force to use BootROM because VC707 doesn't have enough GPIOs for QSPI */
    case PeripheryMaskROMKey => List(
      MaskROMParams(address = BigInt(0x20000000), depth = 4096, name = "BootROM"))
    case TEEHWResetVector => 0x20000000
    case PeripherySPIFlashKey => List() // disable SPIFlash
    /* Force to disable USB1.1, because there are no pins */
    case PeripheryUSB11HSKey => List()
    case PeripheryRandomKey => up(PeripheryRandomKey, site) map {r =>
      r.copy(board = "Xilinx")
    }
  })
)

// ***************** The simulation flag *****************
class WithSimulation extends Config((site, here, up) => {
  // Frequency is always 100.0 MHz in simulation mode, independent of the board
  case FreqKeyMHz => 100.0
  // Force the DMI to NOT be JTAG
  //case ExportDebug => up(ExportDebug, site).copy(protocols = Set(DMI))
  // Force also the Serial interface
  case testchipip.SerialKey => true
  /* Force to use QSPI-scenario because then the XIP will be put in the BootROM */
  /* Simulation needs the hang function in the XIP */
  case PeripheryMaskROMKey => List(
    MaskROMParams(address = BigInt(0x10000), depth = 4096, name = "BootROM"))
  case TEEHWResetVector => 0x10040 // The hang vector in this case, to support the Serial load
  // DDRPortOther is unsupported
  case DDRPortOther => false
  // USB11HS has problems compiling on verilator.
  case PeripheryUSB11HSKey => List()
  // Random only should include the TRNG version
  case PeripheryRandomKey => up(PeripheryRandomKey, site) map {case r => r.copy(impl = 0) }
})
