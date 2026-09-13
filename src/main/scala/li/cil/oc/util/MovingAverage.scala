package li.cil.oc.util

/**
 * 定长滑动窗口平均值（用于 CPU 时间统计等）。
 *
 * 说明：原实现依赖 `data.sum / size` 的整数除法缓存平均值，行为保持不变。
 */
class MovingAverage(val size: Int) {
  private val data = Array.fill(size)(0)
  private var head = 0
  private var cachedAverage = 0
  private var dirty = true

  def apply(): Int = {
    if (dirty) {
      cachedAverage = data.sum / size
      dirty = false
    }
    cachedAverage
  }

  def +=(value: Int): MovingAverage = {
    data(head) = value
    head = (head + 1) % size
    dirty = true
    this
  }
}
