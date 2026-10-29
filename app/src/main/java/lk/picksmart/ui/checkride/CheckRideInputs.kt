package lk.picksmart.ui.checkride

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import lk.picksmart.domain.usecase.LOCATION_MAX_LENGTH
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lk.picksmart.R
import lk.picksmart.domain.model.FareMode
import lk.picksmart.ui.components.AdaptiveRow
import lk.picksmart.ui.components.FitText
import lk.picksmart.ui.theme.Spacing
import lk.picksmart.util.InputDigits
import lk.picksmart.util.limitDecimalInput

/**
 * A group of Check a Ride inputs on its own card: a small upper-case title, an optional item on
 * the right of it (the fare mode, the total distance), then the inputs. When the title and the
 * item don't both fit on one line (a long translation, a narrow phone, a large font), the item
 * goes under the title at full width ([AdaptiveRow]).
 */
@Composable
internal fun InputSection(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable (stacked: Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val titleText = @Composable {
                Text(
                    title.uppercase(locale),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (action == null) titleText() else AdaptiveRow(start = titleText, end = action)
            content()
        }
    }
}

/**
 * Gross fare / Payout after fee as two small pills for the fare card's title row. The row keeps
 * 48 dp of height for touch; the pills inside are drawn smaller. With [fill] (under the title,
 * [AdaptiveRow]) the two share the full width, each label keeping to two lines at most.
 */
@Composable
internal fun FareModePills(selected: FareMode, fill: Boolean, onSelect: (FareMode) -> Unit) {
    Row(
        Modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .minimumInteractiveComponentSize()
            .height(IntrinsicSize.Min)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(2.dp)
            .selectableGroup(),
    ) {
        FareMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 32.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(mode) })
                    .padding(horizontal = Spacing.s + 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                FitText(
                    stringResource(
                        when (mode) {
                            FareMode.GROSS_BEFORE_COMMISSION -> R.string.check_fare_mode_gross
                            FareMode.PAYOUT_AFTER_COMMISSION -> R.string.check_fare_mode_payout
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lines = 2,
                    minScale = 0.8f,
                )
            }
        }
    }
}

/**
 * The fare: "Rs." and a large number in a box with the brand outline while it has focus, and the
 * "+50" / "+100" buttons. At large font sizes ([stacked]) the
 * add buttons move under the box so the number keeps its width.
 */
@Composable
internal fun FareBox(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    focusRequester: FocusRequester,
    error: String?,
    stacked: Boolean,
    onAdd: (rupees: Long) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = MaterialTheme.shapes.medium
    val colors = MaterialTheme.colorScheme
    val border = when {
        error != null -> BorderStroke(2.dp, colors.error)
        focused -> BorderStroke(2.dp, colors.primary)
        else -> BorderStroke(1.dp, colors.outline)
    }
    val addButtons = @Composable {
        QUICK_ADD_RUPEES.forEach { QuickAddButton(it) { onAdd(it) } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .background(colors.surface)
                .border(border, shape)
                // A tap anywhere in the box goes to the number, like a normal text field.
                .pointerInput(Unit) {
                    detectTapGestures {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }
                }
                .padding(start = Spacing.m, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(R.string.currency_prefix), style = MaterialTheme.typography.titleLarge, color = colors.primary)
            NumberInput(
                value = value,
                onValueChange = onValueChange,
                label = label,
                error = error,
                textStyle = MaterialTheme.typography.headlineMedium,
                maxDecimals = 2,
                maxIntegerDigits = InputDigits.MONEY,
                interaction = interaction,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            if (!stacked) addButtons()
        }
        if (stacked) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) { addButtons() }
        }
    }
}

@Composable
private fun QuickAddButton(rupees: Long, onClick: () -> Unit) {
    val description = stringResource(R.string.check_fare_add_description, rupees)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 36.dp)
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.padding(horizontal = Spacing.s), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.check_fare_add, rupees),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * A small number tile: the label at the top, then the number, as in the fee and distance rows.
 * [highlighted] gives the tile the brand outline (the trip distance, the one most offers change).
 * [trailing] sits right of the number (the fee's "Rs." / "%" switch); [note] is a small line under it.
 */
@Composable
internal fun NumberTile(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    suffix: String? = null,
    maxDecimals: Int = 2,
    maxIntegerDigits: Int = InputDigits.MONEY,
    error: String? = null,
    highlighted: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    note: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = MaterialTheme.shapes.medium
    val colors = MaterialTheme.colorScheme
    val border = when {
        error != null -> BorderStroke(2.dp, colors.error)
        focused -> BorderStroke(2.dp, colors.primary)
        highlighted -> BorderStroke(1.dp, colors.primary.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, colors.outlineVariant)
    }
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // The label at the top and the number at the bottom, so tiles in a row line up.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(border, shape)
                .pointerInput(Unit) {
                    detectTapGestures {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }
                }
                .padding(start = Spacing.m, end = if (trailing != null) Spacing.xs else Spacing.m, top = Spacing.s, bottom = Spacing.xs),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            FitText(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlighted || focused) colors.primary else colors.onSurfaceVariant,
                lines = 2,
                minScale = 0.85f,
            )
            Row(
                Modifier.heightIn(min = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (prefix != null) {
                    Text(prefix, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                NumberInput(
                    value = value,
                    onValueChange = onValueChange,
                    label = label,
                    error = error,
                    textStyle = MaterialTheme.typography.titleMedium,
                    maxDecimals = maxDecimals,
                    maxIntegerDigits = maxIntegerDigits,
                    interaction = interaction,
                    imeAction = imeAction,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
                if (suffix != null) {
                    Text(suffix, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                trailing?.invoke()
            }
            if (note != null) {
                Text(note, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
    }
}

/**
 * Tiles side by side with equal widths and one shared height, or stacked full width when
 * [stacked] (large font sizes).
 */
@Composable
internal fun TileRow(stacked: Boolean, tiles: List<@Composable (Modifier) -> Unit>) {
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            tiles.forEach { it(Modifier.fillMaxWidth()) }
        }
    } else {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            tiles.forEach { it(Modifier.weight(1f)) }
        }
    }
}

/** The bare number field inside [FareBox] and [NumberTile]: a "0" placeholder and a numeric keyboard. */
@Composable
private fun NumberInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    textStyle: TextStyle,
    maxDecimals: Int,
    maxIntegerDigits: Int,
    interaction: MutableInteractionSource,
    modifier: Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(limitDecimalInput(value, it, maxDecimals, maxIntegerDigits)) },
        singleLine = true,
        textStyle = textStyle.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (maxDecimals > 0) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction,
        ),
        interactionSource = interaction,
        modifier = modifier.semantics {
            contentDescription = label
            if (error != null) error(error)
        },
        decorationBox = { field ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(stringResource(R.string.placeholder_zero), style = textStyle, color = colors.onSurfaceVariant.copy(alpha = 0.6f))
                }
                field()
            }
        },
    )
}

/** The fare buttons, in rupees. */
private val QUICK_ADD_RUPEES = listOf(50L, 100L)

/**
 * The trip's optional pickup and "Where to" places, as two one-line text boxes marked with a pin
 * and a flag. They never change the result; they are saved with the trip and shown in History.
 * With [collapsible] (Check a Ride, where speed comes first) the card starts as one "Add pickup
 * and where to" button and opens on a tap, or at once when a place is already filled.
 */
@Composable
internal fun LocationFields(
    pickup: String,
    dropOff: String,
    onPickupChange: (String) -> Unit,
    onDropOffChange: (String) -> Unit,
    collapsible: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (collapsible && !open && pickup.isEmpty() && dropOff.isEmpty()) {
        TextButton(onClick = { open = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(painterResource(R.drawable.ic_pin), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text(stringResource(R.string.trip_add_locations))
        }
        return
    }
    InputSection(title = stringResource(R.string.trip_locations_optional)) {
        LocationBox(pickup, onPickupChange, stringResource(R.string.trip_pickup_location), R.drawable.ic_pin, ImeAction.Next)
        LocationBox(dropOff, onDropOffChange, stringResource(R.string.trip_drop_off_location), R.drawable.ic_flag, ImeAction.Done)
    }
}

/** One place: an icon, then the text, with the label as its placeholder and the brand outline while focused. */
@Composable
private fun LocationBox(value: String, onValueChange: (String) -> Unit, label: String, @DrawableRes iconRes: Int, imeAction: ImeAction) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val border = if (focused) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant)
    val textStyle = MaterialTheme.typography.bodyLarge
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.replace('\n', ' ').take(LOCATION_MAX_LENGTH)) },
        singleLine = true,
        textStyle = textStyle.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = imeAction),
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.touchTarget)
                    .clip(shape)
                    .background(colors.surface)
                    .border(border, shape)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = null,
                    tint = if (focused) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(label, style = textStyle, color = colors.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    field()
                }
            }
        },
    )
}
