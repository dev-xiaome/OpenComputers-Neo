package li.cil.oc.integration.util

import li.cil.oc.Settings

object Power {
  // Applied Energistics 2

  def fromAE(value: Double) = value * Settings.get.ratioAppliedEnergistics2

  def toAE(value: Double): Double = value / Settings.get.ratioAppliedEnergistics2

  // Mekanism

  def fromJoules(value: Double) = value * Settings.get.ratioMekanism

  def toJoules(value: Double): Double = value / Settings.get.ratioMekanism

  // Redstone Flux

  def fromRF(value: Int) = value * Settings.get.ratioRedstoneFlux

  def toRF(value: Double): Int = (value / Settings.get.ratioRedstoneFlux).toInt

}
