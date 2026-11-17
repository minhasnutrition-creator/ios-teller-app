package com.minhas.teller.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.navigation.NavController
import com.minhas.teller.R
import com.minhas.teller.navigation.Screen
import com.minhas.teller.utils.LicenseUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Star data class for twinkling space effect
data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val baseAlpha: Float,
    val twinkleSpeed: Float,
    val phaseOffset: Float,
    val isSparkle: Boolean,
    // Real starfields are not uniformly white — stars run from hot blue-white to cool amber.
    val tint: Color
)

// Planet Surface Styles for High Definition rendering
enum class PlanetType {
    GAS_GIANT,
    CRATERED,
    EARTH_LIKE
}

// A planet is fully described up front so its artwork can be baked once into a sprite instead
// of being re-rendered every frame. The detailed look (layered shading, surface texture, ring
// system) costs dozens of gradient shaders per planet — paying that 60 times a second for nine
// planets froze the splash for seconds at a time on real hardware.
private data class PlanetSpec(
    val radiusDp: Float,
    val gradientColors: List<Color>,
    val glowColor: Color,
    val type: PlanetType,
    val hasRing: Boolean = false,
    val ringRotation: Float = 0f,
    // Fixed longitude of the surface features, so each planet shows a different face.
    val surfaceLongitude: Float = 0f
)

// How far beyond the planet radius the sprite must reach to contain its ring and rim bloom.
private const val SPRITE_EXTENT = 2.6f

private val PLANET_SPECS = listOf(
    // --- Inner orbit ---
    PlanetSpec(11f, listOf(Color(0xFF00F2FE), Color(0xFF4FACFE)), Color(0xFF00E5FF), PlanetType.EARTH_LIKE, surfaceLongitude = 0.00f),
    PlanetSpec(9f, listOf(Color(0xFFE040FB), Color(0xFFFF00E5)), Color(0xFFFF00E5), PlanetType.CRATERED, surfaceLongitude = 0.35f),
    PlanetSpec(10f, listOf(Color(0xFFFF9100), Color(0xFFFFD600)), Color(0xFFFF9100), PlanetType.GAS_GIANT, surfaceLongitude = 0.70f),
    // --- Middle orbit (the ringed showpieces) ---
    PlanetSpec(17f, listOf(Color(0xFFFF5E62), Color(0xFFFF9966)), Color(0xFFFF6B6B), PlanetType.GAS_GIANT, hasRing = true, ringRotation = 25f, surfaceLongitude = 0.15f),
    PlanetSpec(15f, listOf(Color(0xFF1DE9B6), Color(0xFF00BFA5)), Color(0xFF1DE9B6), PlanetType.EARTH_LIKE, hasRing = true, ringRotation = -15f, surfaceLongitude = 0.55f),
    PlanetSpec(16f, listOf(Color(0xFFFFD600), Color(0xFFFF9100)), Color(0xFFFFD600), PlanetType.CRATERED, hasRing = true, ringRotation = 45f, surfaceLongitude = 0.80f),
    // --- Outer orbit ---
    PlanetSpec(14f, listOf(Color(0xFFD4FC79), Color(0xFF96E6A1)), Color(0xFF00E676), PlanetType.EARTH_LIKE, surfaceLongitude = 0.25f),
    PlanetSpec(13f, listOf(Color(0xFF64B5F6), Color(0xFF1565C0)), Color(0xFF2979FF), PlanetType.GAS_GIANT, surfaceLongitude = 0.60f),
    PlanetSpec(12f, listOf(Color(0xFFFF4081), Color(0xFF7C4DFF)), Color(0xFFFF4081), PlanetType.CRATERED, surfaceLongitude = 0.90f)
)

// Saturn-style ring system. Drawn in two passes — the far half behind the planet body and the
// near half over it — which is what actually sells the 3D depth; a single full ellipse always
// reads as a flat decal sitting on top of the disc.
private fun DrawScope.drawRingSystem(
    x: Float,
    y: Float,
    radius: Float,
    ringColor: Color,
    ringRotation: Float,
    frontHalf: Boolean
) {
    val tiltSquash = 0.27f
    // semiMajor factor, stroke width factor, peak alpha — the gap between band 2 and 3 stands
    // in for the Cassini division.
    val bands = listOf(
        Triple(1.52f, 0.20f, 0.60f),
        Triple(1.82f, 0.11f, 0.40f),
        Triple(2.18f, 0.14f, 0.26f)
    )
    val reach = radius * 3.2f
    withTransform({ rotate(ringRotation, Offset(x, y)) }) {
        clipRect(
            left = x - reach,
            top = if (frontHalf) y else y - reach,
            right = x + reach,
            bottom = if (frontHalf) y + reach else y
        ) {
            bands.forEach { (semiFactor, strokeFactor, peakAlpha) ->
                val semi = radius * semiFactor
                val minor = semi * tiltSquash
                drawOval(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            ringColor.copy(alpha = peakAlpha * 0.5f),
                            ringColor.copy(alpha = peakAlpha),
                            ringColor.copy(alpha = peakAlpha * 0.5f),
                            Color.Transparent
                        ),
                        start = Offset(x - semi, y),
                        end = Offset(x + semi, y)
                    ),
                    topLeft = Offset(x - semi, y - minor),
                    size = Size(semi * 2, minor * 2),
                    style = Stroke(width = radius * strokeFactor)
                )
            }
        }
    }
}

// Renders one fully-detailed planet. Called once per planet at bake time, never per frame, so
// it can afford as much shading detail as the look needs.
private fun DrawScope.drawPlanetSprite(x: Float, y: Float, radius: Float, spec: PlanetSpec) {
    // Single shared light direction (upper-left) across every planet, so the whole system reads
    // as lit by one star rather than nine independent stickers.
    val lightX = x - radius * 0.42f
    val lightY = y - radius * 0.42f

    // Maps a longitude (0..1 around the sphere) to an on-screen horizontal offset plus a
    // "facing" term: positive means the feature is on the visible hemisphere, and its magnitude
    // foreshortens the feature as it approaches the limb.
    fun longitude(lon: Float): Pair<Float, Float> {
        val a = lon * 2f * Math.PI.toFloat()
        return Pair(sin(a), cos(a))
    }

    val spin = spec.surfaceLongitude

    // Tight rim bloom (the wide pulsing halo is drawn live, not baked, so it can breathe)
    drawCircle(
        brush = Brush.radialGradient(
            0.55f to Color.Transparent,
            0.82f to spec.glowColor.copy(alpha = 0.30f),
            1.0f to Color.Transparent,
            center = Offset(x, y),
            radius = radius * 1.42f
        ),
        radius = radius * 1.42f,
        center = Offset(x, y)
    )

    // Far half of the ring, behind the planet body
    if (spec.hasRing) drawRingSystem(x, y, radius, spec.glowColor, spec.ringRotation, frontHalf = false)

    // Planet body with custom surface textures
    val planetPath = Path().apply {
        addOval(Rect(Offset(x - radius, y - radius), Size(radius * 2, radius * 2)))
    }
    clipPath(planetPath) {
        drawCircle(
            brush = Brush.linearGradient(
                colors = spec.gradientColors,
                start = Offset(x - radius, y - radius),
                end = Offset(x + radius, y + radius)
            ),
            radius = radius,
            center = Offset(x, y)
        )

        when (spec.type) {
            PlanetType.GAS_GIANT -> {
                // Jupiter-style banding. Each band is an oval whose width is the actual chord
                // of the sphere at that latitude, so bands narrow toward the poles and curve
                // with the body instead of running flat edge-to-edge.
                val bandCount = 11
                for (i in 0 until bandCount) {
                    val t = (i + 0.5f) / bandCount
                    val dy = (t * 2f - 1f) * radius
                    val halfChord = sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
                    val bandHeight = (radius * 2f / bandCount) * 1.3f
                    val shade = sin(i * 1.85f + 0.55f)
                    val bandColor = if (shade >= 0f) {
                        Color.White.copy(alpha = 0.15f * shade)
                    } else {
                        Color(0xFF3E2723).copy(alpha = 0.19f * -shade)
                    }
                    drawOval(
                        color = bandColor,
                        topLeft = Offset(x - halfChord, y + dy - bandHeight / 2f),
                        size = Size(halfChord * 2f, bandHeight)
                    )
                }
                // Great-Red-Spot style storm
                val (spotXf, spotFacing) = longitude(spin)
                if (spotFacing > 0.05f) {
                    val spotW = radius * 0.40f * spotFacing
                    val spotH = radius * 0.24f
                    val spotCx = x + spotXf * radius * 0.74f
                    val spotCy = y - radius * 0.16f
                    drawOval(
                        color = Color(0xFFBF360C).copy(alpha = 0.40f * spotFacing),
                        topLeft = Offset(spotCx - spotW, spotCy - spotH / 2f),
                        size = Size(spotW * 2f, spotH)
                    )
                    drawOval(
                        color = Color(0xFFFFCCBC).copy(alpha = 0.18f * spotFacing),
                        topLeft = Offset(spotCx - spotW * 0.6f, spotCy - spotH * 0.3f),
                        size = Size(spotW * 1.2f, spotH * 0.6f)
                    )
                }
            }
            PlanetType.CRATERED -> {
                // Dark mare basin for large-scale surface variation
                drawCircle(
                    color = Color.Black.copy(alpha = 0.14f),
                    radius = radius * 0.62f,
                    center = Offset(x + radius * 0.22f, y + radius * 0.26f)
                )
                // Craters: a lit rim offset toward the light plus a shadowed floor offset away
                // from it — that pairing is what reads as real depth.
                val craters = listOf(
                    Triple(0.30f, -0.22f, 0.25f),
                    Triple(-0.38f, 0.28f, 0.19f),
                    Triple(0.06f, 0.44f, 0.27f),
                    Triple(-0.16f, -0.46f, 0.14f),
                    Triple(0.50f, 0.14f, 0.12f),
                    Triple(-0.54f, -0.06f, 0.11f),
                    Triple(0.18f, -0.02f, 0.09f),
                    Triple(-0.08f, 0.14f, 0.07f)
                )
                craters.forEach { (cxF, cyF, crF) ->
                    val ccx = x + radius * cxF
                    val ccy = y + radius * cyF
                    val crr = radius * crF
                    drawCircle(
                        color = Color.White.copy(alpha = 0.18f),
                        radius = crr * 1.14f,
                        center = Offset(ccx - crr * 0.14f, ccy - crr * 0.14f)
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.28f),
                        radius = crr,
                        center = Offset(ccx + crr * 0.07f, ccy + crr * 0.07f)
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.16f),
                        radius = crr * 0.70f,
                        center = Offset(ccx + crr * 0.20f, ccy + crr * 0.20f)
                    )
                }
            }
            PlanetType.EARTH_LIKE -> {
                // Deepen the ocean toward the limb for a water-world base
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Color.Transparent,
                        0.6f to Color(0xFF01579B).copy(alpha = 0.22f),
                        1.0f to Color(0xFF002171).copy(alpha = 0.50f),
                        center = Offset(x, y),
                        radius = radius
                    ),
                    radius = radius,
                    center = Offset(x, y)
                )

                // Irregular landmasses built from curves rather than plain circles
                val landmasses = listOf(
                    Triple(0.00f, -0.18f, 0.52f),
                    Triple(0.34f, 0.30f, 0.40f),
                    Triple(0.66f, -0.40f, 0.30f)
                )
                landmasses.forEach { (lonOffset, latF, scaleF) ->
                    val (xf, facing) = longitude(spin + lonOffset)
                    if (facing > 0.08f) {
                        val cx = x + xf * radius * 0.70f
                        val cy = y + radius * latF
                        val s = radius * scaleF
                        val w = s * facing
                        val land = Path().apply {
                            moveTo(cx, cy - s * 0.9f)
                            cubicTo(
                                cx + w * 0.95f, cy - s * 0.75f,
                                cx + w * 1.10f, cy + s * 0.30f,
                                cx + w * 0.35f, cy + s * 0.85f
                            )
                            cubicTo(
                                cx - w * 0.20f, cy + s * 1.10f,
                                cx - w * 0.95f, cy + s * 0.50f,
                                cx - w * 0.80f, cy - s * 0.15f
                            )
                            cubicTo(
                                cx - w * 0.70f, cy - s * 0.70f,
                                cx - w * 0.35f, cy - s * 0.95f,
                                cx, cy - s * 0.9f
                            )
                            close()
                        }
                        drawPath(land, Color(0xFF2E7D32).copy(alpha = 0.60f * facing))
                        drawPath(
                            land,
                            Color(0xFF9CCC65).copy(alpha = 0.24f * facing),
                            style = Stroke(width = radius * 0.045f)
                        )
                    }
                }

                // Polar ice caps
                drawOval(
                    color = Color.White.copy(alpha = 0.50f),
                    topLeft = Offset(x - radius * 0.58f, y - radius * 1.06f),
                    size = Size(radius * 1.16f, radius * 0.44f)
                )
                drawOval(
                    color = Color.White.copy(alpha = 0.40f),
                    topLeft = Offset(x - radius * 0.52f, y + radius * 0.66f),
                    size = Size(radius * 1.04f, radius * 0.40f)
                )

                // Weather layer
                val clouds = listOf(
                    Triple(0.12f, -0.52f, 0.46f),
                    Triple(0.48f, 0.08f, 0.56f),
                    Triple(0.80f, 0.46f, 0.40f)
                )
                clouds.forEach { (lonOffset, latF, wF) ->
                    val (xf, facing) = longitude(spin * 1.35f + lonOffset)
                    if (facing > 0.1f) {
                        val cw = radius * wF * facing
                        drawOval(
                            color = Color.White.copy(alpha = 0.28f * facing),
                            topLeft = Offset(
                                x + xf * radius * 0.62f - cw / 2f,
                                y + radius * latF - radius * 0.07f
                            ),
                            size = Size(cw, radius * 0.15f)
                        )
                    }
                }
            }
        }
    }

    // Terminator — day/night falloff from the shared light position
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to Color.Transparent,
            0.42f to Color.Black.copy(alpha = 0.10f),
            0.72f to Color.Black.copy(alpha = 0.52f),
            1.0f to Color.Black.copy(alpha = 0.90f),
            center = Offset(lightX, lightY),
            radius = radius * 1.78f
        ),
        radius = radius,
        center = Offset(x, y)
    )

    // Limb darkening — keeps the silhouette edge from looking cut out
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to Color.Transparent,
            0.72f to Color.Transparent,
            1.0f to Color.Black.copy(alpha = 0.42f),
            center = Offset(x, y),
            radius = radius
        ),
        radius = radius,
        center = Offset(x, y)
    )

    // Rim light along the lit edge
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(
                spec.glowColor.copy(alpha = 0.80f),
                spec.glowColor.copy(alpha = 0.12f),
                Color.Transparent
            ),
            start = Offset(x - radius, y - radius),
            end = Offset(x + radius, y + radius)
        ),
        radius = radius * 0.97f,
        center = Offset(x, y),
        style = Stroke(width = radius * 0.08f)
    )

    // Soft specular highlight
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0f)),
            center = Offset(lightX, lightY),
            radius = radius * 0.52f
        ),
        radius = radius * 0.52f,
        center = Offset(lightX, lightY)
    )

    // Near half of the ring, passing in front of the planet
    if (spec.hasRing) drawRingSystem(x, y, radius, spec.glowColor, spec.ringRotation, frontHalf = true)
}

private class PlanetSprite(val bitmap: ImageBitmap, val drawDim: Int)

// Bakes every planet into an ImageBitmap. Each frame then only has to blit nine bitmaps
// instead of rebuilding hundreds of gradient shaders. Baking at 2x and blitting down
// supersamples the result, so the fine surface detail and ring edges stay crisp instead of
// aliasing at these small on-screen sizes.
private fun bakePlanetSprites(density: Density): List<PlanetSprite> {
    val supersample = 2
    return PLANET_SPECS.map { spec ->
        val r = with(density) { spec.radiusDp.dp.toPx() }
        val drawDim = (r * SPRITE_EXTENT * 2f).toInt().coerceAtLeast(8)
        val bakeDim = drawDim * supersample
        val bitmap = ImageBitmap(bakeDim, bakeDim)
        CanvasDrawScope().draw(
            density = Density(density.density * supersample, density.fontScale),
            layoutDirection = LayoutDirection.Ltr,
            canvas = GraphicsCanvas(bitmap),
            size = Size(bakeDim.toFloat(), bakeDim.toFloat())
        ) {
            drawPlanetSprite(bakeDim / 2f, bakeDim / 2f, r * supersample, spec)
        }
        PlanetSprite(bitmap, drawDim)
    }
}

// Baking nine supersampled sprites is a one-off cost, but it is heavy enough to blow the first
// frame's budget if it runs during composition — so it happens off the main thread and the
// planets fade in a moment later rather than stalling the splash on arrival.
@Composable
private fun rememberPlanetSprites(): List<PlanetSprite> {
    val density = LocalDensity.current
    val sprites by produceState(initialValue = emptyList<PlanetSprite>(), density.density) {
        value = withContext(Dispatchers.Default) { bakePlanetSprites(density) }
    }
    return sprites
}

// Futuristic Space Splash Screen Background with Orbiting Glassmorphic Planets
@Composable
fun SpaceSplashAnimation(
    logoPulse: Float,
    glowIntensity: Float,
    orbit1: Float,
    orbit2: Float,
    orbit3: Float,
    timeState: Float
) {
    // Deep-space palette. Real space is essentially black — the previous near-navy base plus
    // heavy blue/purple nebula washes read as a blue gradient rather than sky.
    val spaceBlack = Color(0xFF000000)
    val spaceVoid = Color(0xFF02030A)
    val spaceDust = Color(0xFF070A16)
    val neonCyan = Color(0xFF00E5FF)
    val neonPurple = Color(0xFF7C4DFF)
    val neonPink = Color(0xFFFF00E5)

    val planetSprites = rememberPlanetSprites()

    // Generate static stars. Far more of them, mostly very small and faint, which is what makes
    // a starfield read as depth instead of as scattered dots.
    val stars = remember {
        val tints = listOf(
            Color(0xFFFFFFFF),
            Color(0xFFDCE7FF), // hot blue-white
            Color(0xFFBFD4FF),
            Color(0xFFFFF1D6), // cool amber
            Color(0xFFFFE0B2)
        )
        List(320) { index ->
            val tiny = index % 4 != 0
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = if (tiny) Random.nextFloat() * 0.5f + 0.25f else Random.nextFloat() * 1.1f + 0.5f,
                baseAlpha = if (tiny) Random.nextFloat() * 0.35f + 0.12f else Random.nextFloat() * 0.55f + 0.35f,
                twinkleSpeed = Random.nextFloat() * 1.5f + 0.5f,
                phaseOffset = Random.nextFloat() * 2f * Math.PI.toFloat(),
                isSparkle = index % 40 == 0,
                tint = tints[Random.nextInt(tints.size)]
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerX = canvasWidth / 2
        val centerY = canvasHeight / 2
        
        // Space background: black base with only the faintest lift toward the centre
        drawRect(color = spaceBlack)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(spaceDust, spaceVoid, spaceBlack),
                center = Offset(centerX, centerY),
                radius = canvasWidth * 1.0f
            )
        )

        // Nebulae: far dimmer than before and pushed to the corners, so they suggest distant
        // gas rather than tinting the whole screen blue.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(neonPurple.copy(alpha = 0.07f * glowIntensity), Color.Transparent),
                center = Offset(canvasWidth * 0.18f, canvasHeight * 0.22f),
                radius = canvasWidth * 0.55f
            ),
            radius = canvasWidth * 0.55f,
            center = Offset(canvasWidth * 0.18f, canvasHeight * 0.22f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF00897B).copy(alpha = 0.05f * glowIntensity), Color.Transparent),
                center = Offset(canvasWidth * 0.86f, canvasHeight * 0.78f),
                radius = canvasWidth * 0.5f
            ),
            radius = canvasWidth * 0.5f,
            center = Offset(canvasWidth * 0.86f, canvasHeight * 0.78f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(neonPink.copy(alpha = 0.035f * glowIntensity), Color.Transparent),
                center = Offset(canvasWidth * 0.72f, canvasHeight * 0.14f),
                radius = canvasWidth * 0.4f
            ),
            radius = canvasWidth * 0.4f,
            center = Offset(canvasWidth * 0.72f, canvasHeight * 0.14f)
        )

        // Draw twinkling stars
        stars.forEach { star ->
            val starX = star.x * canvasWidth
            val starY = star.y * canvasHeight
            val twinkle = sin(timeState * star.twinkleSpeed + star.phaseOffset)
            val alphaFactor = 0.6f + 0.4f * twinkle
            val alpha = star.baseAlpha * alphaFactor

            if (star.isSparkle && alpha > 0.4f) {
                val sparkleSize = star.size * 3.dp.toPx() * alphaFactor
                val path = Path().apply {
                    moveTo(starX, starY - sparkleSize)
                    quadraticTo(starX, starY, starX + sparkleSize, starY)
                    quadraticTo(starX, starY, starX, starY + sparkleSize)
                    quadraticTo(starX, starY, starX - sparkleSize, starY)
                    quadraticTo(starX, starY, starX, starY - sparkleSize)
                }
                drawPath(path, color = star.tint.copy(alpha = alpha))
            } else {
                drawCircle(
                    color = star.tint.copy(alpha = alpha),
                    radius = star.size.dp.toPx(),
                    center = Offset(starX, starY)
                )
            }
        }
        
        // Orbit diameters matching premium design. The outer orbit is capped against canvas
        // width so the (now much larger) outer planets stay on-screen on normal phone widths
        // instead of spending most of their sweep clipped off the left and right edges.
        val orbit1Radius = 90.dp.toPx()
        val orbit2Radius = 150.dp.toPx()
        val orbit3Radius = minOf(210.dp.toPx(), canvasWidth * 0.46f)
        
        // Orbit ring 1 (Dashed)
        drawCircle(
            color = neonCyan.copy(alpha = 0.12f),
            radius = orbit1Radius,
            center = Offset(centerX, centerY),
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), orbit1 * 0.5f)
            )
        )
        
        // Orbit ring 2 (Dashed)
        drawCircle(
            color = neonPurple.copy(alpha = 0.1f),
            radius = orbit2Radius,
            center = Offset(centerX, centerY),
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), -orbit2 * 0.4f)
            )
        )
        
        // Orbit ring 3 (Dashed)
        drawCircle(
            color = neonPink.copy(alpha = 0.08f),
            radius = orbit3Radius,
            center = Offset(centerX, centerY),
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(25f, 25f), orbit3 * 0.3f)
            )
        )
        
        // Motion wake behind each planet. Trail blobs are deliberately a fraction of the planet
        // radius — at full size the larger planets would drag a row of near-identical copies
        // behind them instead of a sense of speed.
        fun drawPlanetTrail(
            orbitRadius: Float,
            currentAngleRad: Double,
            color: Color,
            sizePx: Float,
            glow: Float
        ) {
            for (i in 1..5) {
                val trailAngle = currentAngleRad - i * 0.035f
                val trailX = centerX + (orbitRadius * cos(trailAngle)).toFloat()
                val trailY = centerY + (orbitRadius * sin(trailAngle)).toFloat()
                val trailAlpha = 0.20f * (1f - i / 6f) * glow
                drawCircle(
                    color = color.copy(alpha = trailAlpha),
                    radius = sizePx * 0.62f * (1f - i / 7f),
                    center = Offset(trailX, trailY)
                )
            }
        }

        // Places one pre-baked planet: pulsing halo (live, so it can breathe with the glow
        // animation), motion wake, then the sprite itself.
        fun placePlanet(index: Int, orbitRadius: Float, angleRad: Double) {
            val spec = PLANET_SPECS[index]
            val sprite = planetSprites[index]
            val px = centerX + (orbitRadius * cos(angleRad)).toFloat()
            val py = centerY + (orbitRadius * sin(angleRad)).toFloat()
            val r = spec.radiusDp.dp.toPx()

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(spec.glowColor.copy(alpha = 0.22f * glowIntensity), Color.Transparent),
                    center = Offset(px, py),
                    radius = r * 2.6f
                ),
                radius = r * 2.6f,
                center = Offset(px, py)
            )

            drawPlanetTrail(orbitRadius, angleRad, spec.glowColor, r, glowIntensity)

            val half = sprite.drawDim / 2
            drawImage(
                image = sprite.bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(sprite.bitmap.width, sprite.bitmap.height),
                dstOffset = IntOffset((px - half).toInt(), (py - half).toInt()),
                dstSize = IntSize(sprite.drawDim, sprite.drawDim),
                filterQuality = FilterQuality.High
            )
        }

        // Sprites are baked asynchronously; until they land there is simply nothing to place.
        if (planetSprites.size == PLANET_SPECS.size) {
            // ------------------ ORBIT 1 PLANETS (Inner Orbit) ------------------
            placePlanet(0, orbit1Radius, Math.toRadians(orbit1.toDouble()))
            placePlanet(1, orbit1Radius, Math.toRadians((orbit1 + 120f).toDouble()))
            placePlanet(2, orbit1Radius, Math.toRadians((orbit1 + 240f).toDouble()))

            // ------------------ ORBIT 2 PLANETS (Middle Orbit) ------------------
            placePlanet(3, orbit2Radius, Math.toRadians(orbit2.toDouble()))
            placePlanet(4, orbit2Radius, Math.toRadians((orbit2 + 120f).toDouble()))
            placePlanet(5, orbit2Radius, Math.toRadians((orbit2 + 240f).toDouble()))

            // ------------------ ORBIT 3 PLANETS (Outer Orbit) ------------------
            placePlanet(6, orbit3Radius, Math.toRadians(orbit3.toDouble()))
            placePlanet(7, orbit3Radius, Math.toRadians((orbit3 + 120f).toDouble()))
            placePlanet(8, orbit3Radius, Math.toRadians((orbit3 + 240f).toDouble()))
        }
        // Soft backdrop glow for the logo
        val logoGlowSize = 104.dp.toPx() * logoPulse
        val glowRadius = 40.dp.toPx() * glowIntensity
        
        // Outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(neonCyan.copy(alpha = 0.35f * glowIntensity), Color.Transparent),
                center = Offset(centerX, centerY),
                radius = logoGlowSize * 0.7f + glowRadius
            ),
            radius = logoGlowSize * 0.7f + glowRadius,
            center = Offset(centerX, centerY)
        )
        
        // Inner violet glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(neonPurple.copy(alpha = 0.5f * glowIntensity), Color.Transparent),
                center = Offset(centerX, centerY),
                radius = logoGlowSize * 0.5f
            ),
            radius = logoGlowSize * 0.5f,
            center = Offset(centerX, centerY)
        )
    }
}

@Composable
fun SplashScreen(navController: NavController) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        delay(4000) // 4 seconds for space animation

        // Used to navigate straight to CompanyList unconditionally, regardless of trial/license
        // status — meaning even a genuinely trial-expired, unlicensed user was never actually
        // blocked from using the app; enforcement depended entirely on a screen-level banner they
        // could just ignore. This is the hard gate: check status once at cold start and route
        // trial-expired / unlicensed users to the purchase screen instead.
        val (status, _) = LicenseUtils.getAppStatus(context)
        val destination = when (status) {
            LicenseUtils.AppStatus.TRIAL_EXPIRED, LicenseUtils.AppStatus.NOT_LICENSED ->
                Screen.License.route.replace("{reason}", "expired")
            else -> Screen.CompanyList.route
        }

        navController.navigate(destination) {
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val logoPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoPulse"
    )

    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    val orbit1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit1"
    )
    
    val orbit2 by infiniteTransition.animateFloat(
        initialValue = 120f,
        targetValue = 480f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit2"
    )
    
    val orbit3 by infiniteTransition.animateFloat(
        initialValue = 240f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit3"
    )
    
    val timeState by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050714)),
        contentAlignment = Alignment.Center
    ) {
        // Space animation background
        SpaceSplashAnimation(
            logoPulse = logoPulse,
            glowIntensity = glowIntensity,
            orbit1 = orbit1,
            orbit2 = orbit2,
            orbit3 = orbit3,
            timeState = timeState
        )
        
        // TE Logo overlay (exactly centered)
        Image(
            painter = painterResource(id = R.drawable.ic_splash_te),
            contentDescription = "TE Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(104.dp) // Elegant size (not too big)
                .scale(logoPulse)
                .align(Alignment.Center)
        )
        
        // Texts positioned below the logo
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 100.dp) // Clean spacing below the logo
        ) {
            // TELLER text below with NEON GLOW
            Text(
                text = "TELLER",
                color = Color.White,
                fontSize = 32.sp,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 14.sp,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(
                        color = Color(0xFF00E5FF).copy(alpha = 0.7f * glowIntensity),
                        offset = Offset(0f, 0f),
                        blurRadius = 25f * glowIntensity
                    )
                )
            )
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // Tagline with subtle glow
            Text(
                text = "SMART ACCOUNTING",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(
                        color = Color(0xFF7C4DFF).copy(alpha = 0.5f * glowIntensity),
                        offset = Offset(0f, 0f),
                        blurRadius = 10f * glowIntensity
                    )
                )
            )
        }
    }
}
