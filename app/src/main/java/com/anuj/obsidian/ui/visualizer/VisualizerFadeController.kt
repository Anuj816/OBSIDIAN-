package com.anuj.obsidian.ui.visualizer

import kotlin.math.absoluteValue

class VisualizerFadeController {
    // Fade opacity control (0.0 = completely invisible, 1.0 = full opacity)
    private var fadeOpacity = 1.0f
    
    // Fade animation based on audio
    private var targetFadeOpacity = 1.0f
    private var currentAnimatedOpacity = 1.0f
    private var fadeAnimationStartTime = 0L
    private val fadeAnimationDuration = 300L  // milliseconds for smooth fade
    
    // Audio response settings
    private var bassLevel = 0f
    private var midLevel = 0f
    private var trebleLevel = 0f
    
    // Fade modes
    enum class FadeMode {
        DISABLED,           // No fade, full opacity always
        LIGHT_FADE,         // Subtle fade based on user slider
        PULSE_ON_BEAT,      // Fade in on bass hit, fade out on silence
        BREATH,             // Slow breathing in/out fade
        SILENCE_FADE        // Completely fade out after silence
    }
    
    private var fadeMode = FadeMode.DISABLED
    
    // Settings
    private var fadeIntensity = 0.5f  // How much the fade affects opacity (0.0 to 1.0)
    private var silenceDuration = 3000L  // milliseconds before fade to invisible
    private var lastAudioTime = System.currentTimeMillis()
    
    /**
     * Set the manual fade opacity (what the user controls via slider)
     * 0.0 = completely invisible
     * 1.0 = full opacity
     */
    fun setFadeOpacity(opacity: Float) {
        this.fadeOpacity = opacity.coerceIn(0f, 1.0f)
    }
    
    /**
     * Get current fade opacity
     */
    fun getFadeOpacity(): Float = fadeOpacity
    
    /**
     * Set fade mode
     */
    fun setFadeMode(mode: FadeMode) {
        this.fadeMode = mode
    }

    /**
     * Get current fade mode
     */
    fun getFadeMode(): FadeMode = fadeMode
    
    /**
     * Update audio levels for reactive fading
     */
    fun updateAudioLevels(bass: Float, mid: Float, treble: Float) {
        this.bassLevel = bass.coerceIn(0f, 1f)
        this.midLevel = mid.coerceIn(0f, 1f)
        this.trebleLevel = treble.coerceIn(0f, 1f)
        
        // Reset silence timer when audio is detected
        if (bass > 0.1f || mid > 0.1f || treble > 0.1f) {
            lastAudioTime = System.currentTimeMillis()
        }
    }
    
    /**
     * Calculate current animated opacity based on mode and audio
     */
    fun calculateCurrentOpacity(): Float {
        return when (fadeMode) {
            FadeMode.DISABLED -> {
                // No animation, just use manual fade slider
                fadeOpacity
            }
            
            FadeMode.LIGHT_FADE -> {
                // Lightly fade based on audio levels, controlled by user slider
                val audioLevel = (bassLevel * 0.5f + midLevel * 0.3f + trebleLevel * 0.2f).coerceIn(0f, 1f)
                val minOpacity = fadeOpacity * 0.3f  // Don't fade below 30% of user's setting
                val maxOpacity = fadeOpacity  // Cap at user's fade slider
                val animatedOpacity = minOpacity + (audioLevel * (maxOpacity - minOpacity))
                
                smoothTransition(animatedOpacity)
            }
            
            FadeMode.PULSE_ON_BEAT -> {
                // Strong fade in on bass hit, fade out on silence
                val baseOpacity = fadeOpacity
                val pulseFactor = if (bassLevel > 0.5f) {
                    1.0f  // Full opacity on bass hit
                } else {
                    0.4f  // Fade to 40% when quiet
                }
                val targetOpacity = baseOpacity * pulseFactor
                
                smoothTransition(targetOpacity)
            }
            
            FadeMode.BREATH -> {
                // Slow breathing animation (independent of audio)
                val currentTime = System.currentTimeMillis()
                val breathCycle = (currentTime % 4000L) / 4000f  // 4 second cycle
                val breathingOpacity = 0.5f + (kotlin.math.sin(breathCycle * Math.PI * 2) * 0.5f).toFloat()
                val scaledOpacity = fadeOpacity * breathingOpacity
                
                smoothTransition(scaledOpacity)
            }
            
            FadeMode.SILENCE_FADE -> {
                // Completely fade after silence
                val timeSinceLast = System.currentTimeMillis() - lastAudioTime
                val fadeOutOpacity = when {
                    timeSinceLast < silenceDuration -> fadeOpacity
                    timeSinceLast < silenceDuration + 1000L -> {
                        // Fade out over 1 second
                        val progress = (timeSinceLast - silenceDuration) / 1000f
                        fadeOpacity * (1f - progress)
                    }
                    else -> 0f  // Completely invisible
                }
                
                smoothTransition(fadeOutOpacity)
            }
        }
    }
    
    /**
     * Smooth transition between opacity values
     */
    private fun smoothTransition(targetOpacity: Float): Float {
        if ((currentAnimatedOpacity - targetOpacity).absoluteValue < 0.01f) {
            currentAnimatedOpacity = targetOpacity
            return currentAnimatedOpacity
        }
        
        val currentTime = System.currentTimeMillis()
        
        // If target changed, start new transition
        if ((targetFadeOpacity - targetOpacity).absoluteValue > 0.01f) {
            targetFadeOpacity = targetOpacity
            fadeAnimationStartTime = currentTime
        }
        
        val elapsed = currentTime - fadeAnimationStartTime
        val progress = (elapsed / fadeAnimationDuration.toFloat()).coerceIn(0f, 1f)
        
        // Ease-in-out cubic for smooth transition
        val easeProgress = when {
            progress < 0.5f -> 4f * progress * progress * progress
            else -> {
                val p = -2f * progress + 2f
                1f - (p * p * p) / 2f
            }
        }
        
        currentAnimatedOpacity = currentAnimatedOpacity + (targetOpacity - currentAnimatedOpacity) * easeProgress
        
        return currentAnimatedOpacity
    }
    
    /**
     * Set fade intensity (how much audio affects the fade)
     */
    fun setFadeIntensity(intensity: Float) {
        this.fadeIntensity = intensity.coerceIn(0f, 1f)
    }
    
    /**
     * Set silence duration before fade
     */
    fun setSilenceDuration(durationMs: Long) {
        this.silenceDuration = durationMs.coerceAtLeast(1000L)
    }
    
    /**
     * Get fade info for display
     */
    fun getFadeInfo(): FadeInfo {
        return FadeInfo(
            currentOpacity = calculateCurrentOpacity(),
            manualFade = fadeOpacity,
            fadeMode = fadeMode,
            audioLevel = (bassLevel + midLevel + trebleLevel) / 3f
        )
    }
    
    data class FadeInfo(
        val currentOpacity: Float,
        val manualFade: Float,
        val fadeMode: FadeMode,
        val audioLevel: Float
    )
}
