@file:OptIn(ExperimentalSharedTransitionApi::class)

package echo.music.iad1tya.ui.component.floatingtabbar

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingTabBarLayoutTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun expandedBar_keepsCrowdedTabsVisible_atSupportedPhoneWidths() {
    val clicks = mutableStateMapOf<String, Int>()
    listOf(320.dp, 360.dp, 412.dp).forEach { width ->
      clicks.clear()
      composeTestRule.setTabBar(
        width = width,
        tabs = crowdedTabs,
        includeSearch = true,
        clicks = clicks,
      )
      composeTestRule.assertExpandedLayout(
        tabs = crowdedTabs,
        includeSearch = true,
        clicks = clicks,
      )
    }
  }

  @Test
  fun expandedBar_keepsLocalizedLabelsVisible_atFontScales() {
    val clicks = mutableStateMapOf<String, Int>()
    listOf(1.0f, 1.3f).forEach { fontScale ->
      listOf(320.dp, 360.dp, 412.dp).forEach { width ->
        clicks.clear()
        composeTestRule.setTabBar(
          width = width,
          fontScale = fontScale,
          tabs = hungarianTabs,
          includeSearch = true,
          clicks = clicks,
        )
        composeTestRule.assertExpandedLayout(
          tabs = hungarianTabs,
          includeSearch = true,
          clicks = clicks,
          minTouchTarget = 48.dp,
        )
      }
    }
  }

  @Test
  fun expandedBar_allocatesWidth_forReducedTabSets() {
    val clicks = mutableStateMapOf<String, Int>()
    listOf(
        listOf("Home", "Library") to false,
        listOf("Home", "Library", "Social") to false,
        listOf("Home", "Library") to true,
      )
      .forEach { (tabs, includeSearch) ->
        clicks.clear()
        composeTestRule.setTabBar(
          width = 320.dp,
          tabs = tabs,
          includeSearch = includeSearch,
          clicks = clicks,
        )
        composeTestRule.assertExpandedLayout(
          tabs = tabs,
          includeSearch = includeSearch,
          clicks = clicks,
        )
      }
  }

  @Test
  fun expandedBar_preservesSelectionAndReturnsTabsAfterCollapse() {
    val clicks = mutableStateMapOf<String, Int>()
    var selected by mutableStateOf<Any?>(crowdedTabs.first())
    var isInline by mutableStateOf(false)

    composeTestRule.setTabBar(
      width = 360.dp,
      tabs = crowdedTabs,
      includeSearch = true,
      clicks = clicks,
      selectedTabKey = { selected },
      onSelected = { selected = it },
      isInline = { isInline },
    )

    composeTestRule.onClickable(crowdedTabs.last()).performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, clicks[crowdedTabs.last()])
    assertEquals(crowdedTabs.last(), selected)

    composeTestRule.onClickable(SEARCH).performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, clicks[SEARCH])
    assertEquals(SEARCH, selected)

    isInline = true
    composeTestRule.waitForIdle()
    isInline = false
    composeTestRule.waitForIdle()

    crowdedTabs.forEach { label ->
      composeTestRule.onNodeWithTag(iconTag(label), useUnmergedTree = true).assertIsDisplayed()
    }
    composeTestRule.onNodeWithTag(iconTag(SEARCH), useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun expandedBar_keepsSearchSquare_withAndWithoutAccessory() {
    val clicks = mutableStateMapOf<String, Int>()
    listOf(false, true).forEach { includeAccessory ->
      clicks.clear()
      composeTestRule.setTabBar(
        width = 320.dp,
        tabs = crowdedTabs,
        includeSearch = true,
        includeAccessory = includeAccessory,
        clicks = clicks,
      )
      composeTestRule.assertExpandedLayout(
        tabs = crowdedTabs,
        includeSearch = true,
        clicks = clicks,
      )
      if (includeAccessory) {
        val accessory = composeTestRule.onNodeWithTag(ACCESSORY_TAG).getUnclippedBoundsInRoot()
        val search = composeTestRule.onClickable(SEARCH).getUnclippedBoundsInRoot()
        assertTrue(
          "Expanded accessory should stay above Search ($accessory vs $search)",
          accessory.bottom <= search.top + 1.dp,
        )
      }
    }
  }

  private fun ComposeContentTestRule.setTabBar(
    width: Dp,
    tabs: List<String>,
    includeSearch: Boolean,
    clicks: MutableMap<String, Int>,
    fontScale: Float = 1f,
    includeAccessory: Boolean = false,
    selectedTabKey: () -> Any? = { tabs.firstOrNull() },
    onSelected: (Any?) -> Unit = {},
    isInline: () -> Boolean = { false },
  ) {
    setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(
        LocalDensity provides Density(density = density.density, fontScale = fontScale)
      ) {
        MaterialTheme {
          Box(Modifier.requiredWidth(width).padding(horizontal = 16.dp)) {
            TestFloatingTabBar(
              tabs = tabs,
              includeSearch = includeSearch,
              includeAccessory = includeAccessory,
              selectedTabKey = selectedTabKey(),
              isInline = isInline(),
              clicks = clicks,
              onSelected = onSelected,
            )
          }
        }
      }
    }
    waitForIdle()
  }

  private fun ComposeContentTestRule.assertExpandedLayout(
    tabs: List<String>,
    includeSearch: Boolean,
    clicks: MutableMap<String, Int>,
    minTouchTarget: Dp = 48.dp,
  ) {
    val bar = onNodeWithTag(BAR_TAG).getUnclippedBoundsInRoot()
    val labels = if (includeSearch) tabs + SEARCH else tabs
    val cellBounds =
      labels.map { label ->
        val cell = onClickable(label)
        cell.assertIsDisplayed()
        cell.assertWidthIsAtLeast(minTouchTarget)
        cell.assertHeightIsAtLeast(minTouchTarget)
        val bounds = cell.getUnclippedBoundsInRoot()
        val icon = onNodeWithTag(iconTag(label), useUnmergedTree = true)
        icon.assertIsDisplayed()
        val iconBounds = icon.getUnclippedBoundsInRoot()
        assertTrue(
          "$label icon is clipped outside the bar at $iconBounds vs $bar",
          iconBounds.isInside(bar),
        )
        assertTrue("$label cell is outside the bar at $bounds vs $bar", bounds.isInside(bar))
        assertTrue(
          "$label icon has zero size",
          iconBounds.width > 0.dp && iconBounds.height > 0.dp,
        )
        label to bounds
      }

    cellBounds.forEachIndexed { i, (leftLabel, left) ->
      cellBounds.drop(i + 1).forEach { (rightLabel, right) ->
        assertFalse("$leftLabel overlaps $rightLabel ($left vs $right)", left.overlaps(right))
      }
    }

    if (includeSearch) {
      val searchBounds = cellBounds.last().second
      assertTrue(
        "Search should be square, was ${searchBounds.width} x ${searchBounds.height}",
        abs(searchBounds.width.value - searchBounds.height.value) <= 2f,
      )
      assertTrue(searchBounds.width > 0.dp)
    }

    labels.forEach { label ->
      val before = clicks[label] ?: 0
      onClickable(label).performClick()
      waitForIdle()
      assertEquals("$label click was not delivered", before + 1, clicks[label] ?: 0)
    }
  }

  private fun ComposeContentTestRule.onClickable(label: String) =
    onNode(hasClickAction() and hasContentDescription(label))
}

@Composable
private fun TestFloatingTabBar(
  tabs: List<String>,
  includeSearch: Boolean,
  includeAccessory: Boolean,
  selectedTabKey: Any?,
  isInline: Boolean,
  clicks: MutableMap<String, Int>,
  onSelected: (Any?) -> Unit,
) {
  FloatingTabBar(
    isInline = isInline,
    selectedTabKey = selectedTabKey,
    modifier = Modifier.testTag(BAR_TAG).fillMaxWidth(),
    expandedAccessory =
      if (includeAccessory) {
        { modifier, _ -> Box(modifier.fillMaxWidth().height(48.dp).testTag(ACCESSORY_TAG)) }
      } else {
        null
      },
    contentKey = listOf(selectedTabKey, tabs, includeSearch, includeAccessory, isInline),
  ) {
    tabs.forEach { label ->
      tab(
        key = label,
        title = {
          Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
          )
        },
        icon = { TabGlyph(label) },
        onClick = {
          clicks[label] = (clicks[label] ?: 0) + 1
          onSelected(label)
        },
      )
    }
    if (includeSearch) {
      standaloneTab(
        key = SEARCH,
        icon = { TabGlyph(SEARCH) },
        onClick = {
          clicks[SEARCH] = (clicks[SEARCH] ?: 0) + 1
          onSelected(SEARCH)
        },
      )
    }
  }
}

@Composable
private fun TabGlyph(label: String) {
  Box(Modifier.size(24.dp).testTag(iconTag(label)).semantics { contentDescription = label })
}

private fun iconTag(label: String) = "tabIcon-$label"

private fun DpRect.isInside(other: DpRect, tolerance: Dp = 1.dp): Boolean =
  left >= other.left - tolerance &&
    top >= other.top - tolerance &&
    right <= other.right + tolerance &&
    bottom <= other.bottom + tolerance

private fun DpRect.overlaps(other: DpRect, tolerance: Dp = 1.dp): Boolean =
  left < other.right - tolerance &&
    right > other.left + tolerance &&
    top < other.bottom - tolerance &&
    bottom > other.top + tolerance

private val crowdedTabs = listOf("Home", "Recognize", "Library", "Social")
private val hungarianTabs = listOf("Kezdőlap", "Zenefelismerés", "Könyvtár", "Közösségi tér")
private const val SEARCH = "Search"
private const val BAR_TAG = "floatingTabBar"
private const val ACCESSORY_TAG = "expandedAccessory"
