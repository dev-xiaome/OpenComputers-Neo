package li.cil.oc.server.component

import java.util
import com.google.common.hash.Hashing
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network._
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.MutableDataComponentHolder

import java.nio.ByteBuffer
import scala.collection.convert.ImplicitConversionsToJava._

class EEPROM extends AbstractManagedEnvironment with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Neighbors).
    withComponent("eeprom", Visibility.Neighbors).
    withConnector().
    create()

  var codeData = Array.empty[Byte]

  var volatileData = Array.empty[Byte]

  var readonly = false

  var label = "EEPROM"

  def checksum = Hashing.crc32().hashBytes(codeData).toString

  // ----------------------------------------------------------------------- //

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Memory,
    DeviceAttribute.Description -> "EEPROM",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "FlashStick2k",
    DeviceAttribute.Capacity -> Settings.get.eepromSize.toString,
    DeviceAttribute.Size -> Settings.get.eepromSize.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  // ----------------------------------------------------------------------- //

  @Callback(direct = true, doc = """function():string -- Get the currently stored byte array.""")
  def get(context: Context, args: Arguments): Array[AnyRef] = result(codeData)

  @Callback(doc = """function(data:string) -- Overwrite the currently stored byte array.""")
  def set(context: Context, args: Arguments): Array[AnyRef] = {
    if (readonly) {
      return result((), "storage is readonly")
    }
    if (!node.tryChangeBuffer(-Settings.get.eepromWriteCost)) {
      return result((), "not enough energy")
    }
    val newData = args.optByteArray(0, Array.empty[Byte])
    if (newData.length > Settings.get.eepromSize) throw new IllegalArgumentException("not enough space")
    codeData = newData
    context.pause(2) // deliberately slow to discourage use as normal storage medium
    null
  }

  @Callback(direct = true, doc = """function():string -- Get the label of the EEPROM.""")
  def getLabel(context: Context, args: Arguments): Array[AnyRef] = result(label)

  @Callback(doc = """function(data:string):string -- Set the label of the EEPROM.""")
  def setLabel(context: Context, args: Arguments): Array[AnyRef] = {
    if (readonly) {
      return result((), "storage is readonly")
    }
    label = args.optString(0, "EEPROM").trim.take(24)
    if (label.isEmpty) label = "EEPROM"
    result(label)
  }

  @Callback(direct = true, doc = """function():number -- Get the storage capacity of this EEPROM.""")
  def getSize(context: Context, args: Arguments): Array[AnyRef] = result(Settings.get.eepromSize)

  @Callback(direct = true, doc = """function():string -- Get the checksum of the data on this EEPROM.""")
  def getChecksum(context: Context, args: Arguments): Array[AnyRef] = result(checksum)

  @Callback(direct = true, doc = """function(checksum:string):boolean -- Make this EEPROM readonly if it isn't already. This process cannot be reversed!""")
  def makeReadonly(context: Context, args: Arguments): Array[AnyRef] = {
    if (args.checkString(0) == checksum) {
      readonly = true
      result(true)
    }
    else result((), "incorrect checksum")
  }

  @Callback(direct = true, doc = """function():number -- Get the storage capacity of this EEPROM.""")
  def getDataSize(context: Context, args: Arguments): Array[AnyRef] = result(Settings.get.eepromDataSize)

  @Callback(direct = true, doc = """function():string -- Get the currently stored byte array.""")
  def getData(context: Context, args: Arguments): Array[AnyRef] = result(volatileData)

  @Callback(doc = """function(data:string) -- Overwrite the currently stored byte array.""")
  def setData(context: Context, args: Arguments): Array[AnyRef] = {
    if (!node.tryChangeBuffer(-Settings.get.eepromWriteCost)) {
      return result((), "not enough energy")
    }
    val newData = args.optByteArray(0, Array.empty[Byte])
    if (newData.length > Settings.get.eepromDataSize) throw new IllegalArgumentException("not enough space")
    volatileData = newData
    context.pause(1) // deliberately slow to discourage use as normal storage medium
    null
  }

  // ----------------------------------------------------------------------- //

  private def asArray(buffer: ByteBuffer): Array[Byte] = if(buffer.hasArray) {
    buffer.array
  } else {
    val array = Array.fill[Byte](buffer.remaining()) { 0 }
    buffer.get(array)
    array
  }

  override def loadData(holder: DataComponentHolder): Unit = {
    super.loadData(holder)
    for(code <- holder.getComponent(OCComponents.EEPROM_CODE)) codeData = asArray(code)
    for(data <- holder.getComponent(OCComponents.EEPROM_DATA)) volatileData = asArray(data)
    for(label <- holder.getComponent(OCComponents.LABEL)) this.label = label
    this.readonly = holder.getComponent(OCComponents.READONLY) getOrElse false
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    super.saveData(holder)
    holder.setComponent(OCComponents.EEPROM_CODE, ByteBuffer.wrap(codeData))
    holder.setComponent(OCComponents.EEPROM_DATA, ByteBuffer.wrap(volatileData))
    holder.setComponent(OCComponents.LABEL, label)
    holder.setComponent(OCComponents.READONLY, Option.when(readonly) { true })
  }
}
