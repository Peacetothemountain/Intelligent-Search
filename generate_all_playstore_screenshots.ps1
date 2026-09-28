Add-Type -AssemblyName System.Drawing

function Create-RoundedRectanglePath {
    param(
        [float]$x,
        [float]$y,
        [float]$width,
        [float]$height,
        [float]$radius
    )
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $diameter = $radius * 2.0
    $arc = New-Object System.Drawing.RectangleF($x, $y, $diameter, $diameter)
    
    # Top left
    $path.AddArc($arc, 180, 90)
    
    # Top right
    $arc.X = $x + $width - $diameter
    $path.AddArc($arc, 270, 90)
    
    # Bottom right
    $arc.Y = $y + $height - $diameter
    $path.AddArc($arc, 0, 90)
    
    # Bottom left
    $arc.X = $x
    $path.AddArc($arc, 90, 90)
    
    $path.CloseFigure()
    return $path
}

function Clean-DeviceScreen {
    param(
        [System.Drawing.Bitmap]$srcBmp,
        [System.Drawing.Color]$statusBarBgColor
    )
    
    $clean = New-Object System.Drawing.Bitmap($srcBmp.Width, $srcBmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($clean)
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    
    # Draw original image
    $g.DrawImage($srcBmp, 0, 0, $srcBmp.Width, $srcBmp.Height)
    
    # Clean status bar area: y from 0 to 175
    $brush = New-Object System.Drawing.SolidBrush($statusBarBgColor)
    $g.FillRectangle($brush, 0, 0, $srcBmp.Width, 175)
    
    # Smooth blend between y=165 and y=190
    for ($i = 0; $i -lt 25; $i++) {
        $alpha = [int](255 * (1.0 - ($i / 25.0)))
        $blendColor = [System.Drawing.Color]::FromArgb($alpha, $statusBarBgColor.R, $statusBarBgColor.G, $statusBarBgColor.B)
        $blendBrush = New-Object System.Drawing.SolidBrush($blendColor)
        $g.FillRectangle($blendBrush, 0, (165 + $i), $srcBmp.Width, 1)
        $blendBrush.Dispose()
    }
    $brush.Dispose()
    $g.Dispose()
    return $clean
}

function Render-PlayStoreCard {
    param(
        [string]$inputImagePath,
        [string]$outputImagePath,
        [System.Drawing.Color]$statusBarBgColor,
        [System.Drawing.Color]$accentColor,
        [System.Drawing.Color]$ambientGlowColor,
        [string]$badgeText,
        [string]$titleText,
        [string]$subtitleText
    )
    
    $canvasW = 1440
    $canvasH = 3120
    $canvas = New-Object System.Drawing.Bitmap($canvasW, $canvasH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($canvas)
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    
    # --- 1. Background ---
    $bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Point(0, 0)),
        (New-Object System.Drawing.Point(0, $canvasH)),
        [System.Drawing.Color]::FromArgb(255, 14, 16, 22),
        [System.Drawing.Color]::FromArgb(255, 7, 8, 12)
    )
    $g.FillRectangle($bgBrush, 0, 0, $canvasW, $canvasH)
    $bgBrush.Dispose()
    
    # Primary Ambient Spotlight behind Header & Phone
    $glowPath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $glowPath.AddEllipse(-200, -100, 1840, 1800)
    $pbg = New-Object System.Drawing.Drawing2D.PathGradientBrush($glowPath)
    $pbg.CenterPoint = New-Object System.Drawing.PointF(720, 480)
    $pbg.CenterColor = [System.Drawing.Color]::FromArgb(95, $ambientGlowColor.R, $ambientGlowColor.G, $ambientGlowColor.B)
    $pbg.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 7, 8, 12))
    $g.FillPath($pbg, $glowPath)
    $pbg.Dispose()
    $glowPath.Dispose()
    
    # Secondary Ambient Glow near Phone Bottom
    $glowPath2 = New-Object System.Drawing.Drawing2D.GraphicsPath
    $glowPath2.AddEllipse(100, 1800, 1240, 1400)
    $pbg2 = New-Object System.Drawing.Drawing2D.PathGradientBrush($glowPath2)
    $pbg2.CenterPoint = New-Object System.Drawing.PointF(720, 2500)
    $pbg2.CenterColor = [System.Drawing.Color]::FromArgb(50, $ambientGlowColor.R, $ambientGlowColor.G, $ambientGlowColor.B)
    $pbg2.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 7, 8, 12))
    $g.FillPath($pbg2, $glowPath2)
    $pbg2.Dispose()
    $glowPath2.Dispose()
    
    # --- 2. Typography Header ---
    $badgeFont = New-Object System.Drawing.Font("Segoe UI", 16, [System.Drawing.FontStyle]::Bold)
    $titleFont = New-Object System.Drawing.Font("Segoe UI", 44, [System.Drawing.FontStyle]::Bold)
    $subFont = New-Object System.Drawing.Font("Segoe UI", 24, [System.Drawing.FontStyle]::Regular)
    
    # Measure badge
    $badgeSize = $g.MeasureString($badgeText, $badgeFont)
    $badgePadX = 36.0
    $badgePadY = 14.0
    $badgeW = $badgeSize.Width + ($badgePadX * 2.0)
    $badgeH = $badgeSize.Height + ($badgePadY * 2.0)
    $badgeX = (1440.0 - $badgeW) / 2.0
    $badgeY = 100.0
    
    # Draw Badge Pill
    $badgePath = Create-RoundedRectanglePath -x $badgeX -y $badgeY -width $badgeW -height $badgeH -radius ($badgeH / 2.0)
    $badgeBgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(40, $accentColor.R, $accentColor.G, $accentColor.B))
    $g.FillPath($badgeBgBrush, $badgePath)
    $badgeBgBrush.Dispose()
    
    $badgePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(110, $accentColor.R, $accentColor.G, $accentColor.B), 2.0)
    $g.DrawPath($badgePen, $badgePath)
    $badgePen.Dispose()
    $badgePath.Dispose()
    
    # Badge Text
    $badgeTextBrush = New-Object System.Drawing.SolidBrush($accentColor)
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = [System.Drawing.StringAlignment]::Center
    $sf.LineAlignment = [System.Drawing.StringAlignment]::Center
    $g.DrawString($badgeText, $badgeFont, $badgeTextBrush, (New-Object System.Drawing.RectangleF($badgeX, $badgeY, $badgeW, $badgeH)), $sf)
    $badgeTextBrush.Dispose()
    
    # Title Text
    $titleBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 255, 255, 255))
    $titleRect = New-Object System.Drawing.RectangleF(60.0, 180.0, 1320.0, 110.0)
    $g.DrawString($titleText, $titleFont, $titleBrush, $titleRect, $sf)
    $titleBrush.Dispose()
    
    # Subtitle Text
    $subBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 195, 192, 204))
    $subRect = New-Object System.Drawing.RectangleF(80.0, 295.0, 1280.0, 80.0)
    $g.DrawString($subtitleText, $subFont, $subBrush, $subRect, $sf)
    $subBrush.Dispose()
    $sf.Dispose()
    
    # --- 3. Pixel 11 Pro XL Mockup Frame ---
    $phoneScreenW = 1140.0
    $phoneScreenH = 2538.0
    $bezel = 18.0
    $phoneFrameW = $phoneScreenW + ($bezel * 2.0)  # 1176
    $phoneFrameH = $phoneScreenH + ($bezel * 2.0)  # 2574
    $phoneX = (1440.0 - $phoneFrameW) / 2.0        # 132
    $phoneY = 440.0
    $frameRadius = 76.0
    $screenRadius = 58.0
    
    # Realistic Multi-layered Ambient & Contact Shadows
    for ($step = 1; $step -le 4; $step++) {
        $sInflate = $step * 14.0
        $sOffsetY = $step * 12.0
        $sAlpha = [int](36 / $step)
        $shadowPath = Create-RoundedRectanglePath -x ($phoneX - $sInflate) -y ($phoneY - $sInflate + $sOffsetY) -width ($phoneFrameW + ($sInflate * 2.0)) -height ($phoneFrameH + ($sInflate * 2.0)) -radius ($frameRadius + $sInflate)
        $sBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($sAlpha, 0, 0, 0))
        $g.FillPath($sBrush, $shadowPath)
        $sBrush.Dispose()
        $shadowPath.Dispose()
    }
    
    # Physical Hardware Buttons (Right Edge of Pixel 11 Pro XL)
    # Power Button
    $btnX = $phoneX + $phoneFrameW - 1.0
    $pwrY = $phoneY + 480.0
    $pwrPath = Create-RoundedRectanglePath -x $btnX -y $pwrY -width 5.5 -height 96.0 -radius 2.5
    $pwrBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 52, 54, 60))
    $g.FillPath($pwrBrush, $pwrPath)
    $pwrBrush.Dispose()
    $pwrPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(180, 80, 84, 94), 1.0)
    $g.DrawPath($pwrPen, $pwrPath)
    $pwrPen.Dispose()
    $pwrPath.Dispose()
    
    # Volume Rocker
    $volY = $phoneY + 670.0
    $volPath = Create-RoundedRectanglePath -x $btnX -y $volY -width 5.5 -height 225.0 -radius 2.5
    $volBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 52, 54, 60))
    $g.FillPath($volBrush, $volPath)
    $volBrush.Dispose()
    $volPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(180, 80, 84, 94), 1.0)
    $g.DrawPath($volPen, $volPath)
    $volPen.Dispose()
    $volPath.Dispose()
    
    # Outer Frame Body (Matte Anodized Titanium)
    $framePath = Create-RoundedRectanglePath -x $phoneX -y $phoneY -width $phoneFrameW -height $phoneFrameH -radius $frameRadius
    $pt1 = New-Object System.Drawing.PointF([float]$phoneX, [float]$phoneY)
    $pt2 = New-Object System.Drawing.PointF([float]($phoneX + $phoneFrameW), [float]($phoneY + $phoneFrameH))
    $frameBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $pt1,
        $pt2,
        [System.Drawing.Color]::FromArgb(255, 48, 50, 56),
        [System.Drawing.Color]::FromArgb(255, 24, 25, 28)
    )
    $g.FillPath($frameBrush, $framePath)
    $frameBrush.Dispose()
    
    # Outer Metallic Chamfer Border
    $chamferPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(190, 88, 92, 102), 2.0)
    $g.DrawPath($chamferPen, $framePath)
    $chamferPen.Dispose()
    
    # Top Speaker Micro-Slit
    $speakerW = 140.0
    $speakerH = 4.5
    $speakerX = 720.0 - ($speakerW / 2.0)
    $speakerY = $phoneY + 7.0
    $speakerPath = Create-RoundedRectanglePath -x $speakerX -y $speakerY -width $speakerW -height $speakerH -radius 2.0
    $speakerBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 14, 15, 18))
    $g.FillPath($speakerBrush, $speakerPath)
    $speakerBrush.Dispose()
    $speakerPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(120, 50, 52, 58), 1.0)
    $g.DrawPath($speakerPen, $speakerPath)
    $speakerPen.Dispose()
    $speakerPath.Dispose()
    
    # --- 4. Render Cleaned Screen ---
    $screenX = $phoneX + $bezel
    $screenY = $phoneY + $bezel
    $screenPath = Create-RoundedRectanglePath -x $screenX -y $screenY -width $phoneScreenW -height $phoneScreenH -radius $screenRadius
    
    $rawBmp = [System.Drawing.Bitmap]::new($inputImagePath)
    $cleanedScreenBmp = Clean-DeviceScreen -srcBmp $rawBmp -statusBarBgColor $statusBarBgColor
    $rawBmp.Dispose()
    
    $state = $g.Save()
    $g.SetClip($screenPath, [System.Drawing.Drawing2D.CombineMode]::Replace)
    $g.DrawImage($cleanedScreenBmp, $screenX, $screenY, $phoneScreenW, $phoneScreenH)
    $g.Restore($state)
    $cleanedScreenBmp.Dispose()
    
    # Screen Inner Bezel Border (1px Dark Glass Seam)
    $screenSeamPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 10, 10, 12), 1.5)
    $g.DrawPath($screenSeamPen, $screenPath)
    $screenSeamPen.Dispose()
    $screenPath.Dispose()
    $framePath.Dispose()
    
    # --- 5. Pixel 11 Pro XL Punch Hole Camera ---
    $camCX = 720.0
    $camCY = $screenY + 78.0
    $camDiam = 44.0
    $camRad = $camDiam / 2.0
    
    # Outer Ring
    $camRimBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 16, 16, 20))
    $g.FillEllipse($camRimBrush, ($camCX - $camRad - 1.5), ($camCY - $camRad - 1.5), ($camDiam + 3.0), ($camDiam + 3.0))
    $camRimBrush.Dispose()
    
    # Lens Aperture
    $camLensBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 3, 3, 5))
    $g.FillEllipse($camLensBrush, ($camCX - $camRad), ($camCY - $camRad), $camDiam, $camDiam)
    $camLensBrush.Dispose()
    
    # Specular Glass Reflection
    $camSpecBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(70, 75, 95, 145))
    $g.FillEllipse($camSpecBrush, ($camCX - 9.0), ($camCY - 10.0), 9.0, 9.0)
    $camSpecBrush.Dispose()
    
    # Ensure target directories exist
    $targetDir = [System.IO.Path]::GetDirectoryName($outputImagePath)
    if (-not (Test-Path $targetDir)) {
        [System.IO.Directory]::CreateDirectory($targetDir) | Out-Null
    }
    
    $canvas.Save($outputImagePath, [System.Drawing.Imaging.ImageFormat]::Png)
    $badgeFont.Dispose()
    $titleFont.Dispose()
    $subFont.Dispose()
    $g.Dispose()
    $canvas.Dispose()
    Write-Host "Card generated successfully: $outputImagePath"
}

# Output Folders
$downloadsDir = "C:\Users\caref\Downloads\PlayStore_Screenshots"
$docsDir = "F:\Intelligent-Search\docs\images"

if (-not (Test-Path $downloadsDir)) {
    [System.IO.Directory]::CreateDirectory($downloadsDir) | Out-Null
}
if (-not (Test-Path $docsDir)) {
    [System.IO.Directory]::CreateDirectory($docsDir) | Out-Null
}

$cards = @(
    @{
        Input = "F:\Intelligent-Search\screen_home_nokb.png"
        Filename = "01_Instant_Device_Search.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 18, 15, 22)
        Accent = [System.Drawing.Color]::FromArgb(255, 208, 188, 255)
        Glow = [System.Drawing.Color]::FromArgb(255, 140, 90, 230)
        Badge = "INSTANT DEVICE SEARCH"
        Title = "Find Everything in Real Time"
        Subtitle = "Lightning-fast access to apps, contacts, files & shortcuts"
    },
    @{
        Input = "F:\Intelligent-Search\screen_query_nokb.png"
        Filename = "02_Context_Aware_Search.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 18, 15, 22)
        Accent = [System.Drawing.Color]::FromArgb(255, 168, 199, 250)
        Glow = [System.Drawing.Color]::FromArgb(255, 59, 120, 231)
        Badge = "INTELLIGENT RESULTS"
        Title = "Direct In-App Actions"
        Subtitle = "Top hits, navigation chips, Google Lens & web suggestions"
    },
    @{
        Input = "F:\Intelligent-Search\screen_calc_nokb.png"
        Filename = "03_Builtin_Math_Engine.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 18, 15, 22)
        Accent = [System.Drawing.Color]::FromArgb(255, 165, 242, 215)
        Glow = [System.Drawing.Color]::FromArgb(255, 29, 154, 122)
        Badge = "BUILT-IN MATH ENGINE"
        Title = "Instant Live Calculations"
        Subtitle = "Formulas, conversions, and one-tap copy directly in search"
    },
    @{
        Input = "F:\Intelligent-Search\screen_widget.png"
        Filename = "04_Expressive_Home_Widgets.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 16, 13, 17)
        Accent = [System.Drawing.Color]::FromArgb(255, 232, 180, 253)
        Glow = [System.Drawing.Color]::FromArgb(255, 156, 60, 231)
        Badge = "EXPRESSIVE HOME WIDGETS"
        Title = "Material You Widget Studio"
        Subtitle = "Dynamic system & Material search bars with live preview"
    },
    @{
        Input = "F:\Intelligent-Search\screen_appearance.png"
        Filename = "05_Atmospheric_Themes.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 16, 13, 17)
        Accent = [System.Drawing.Color]::FromArgb(255, 255, 179, 184)
        Glow = [System.Drawing.Color]::FromArgb(255, 217, 72, 98)
        Badge = "INFINITE CUSTOMIZATION"
        Title = "Atmospheric Themes & Blur"
        Subtitle = "Matrix rain animation, shape morphing & squiggly sliders"
    },
    @{
        Input = "F:\Intelligent-Search\screen_security.png"
        Filename = "06_Hardware_Security_Titan_M3.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 11, 9, 12)
        Accent = [System.Drawing.Color]::FromArgb(255, 146, 211, 245)
        Glow = [System.Drawing.Color]::FromArgb(255, 26, 136, 201)
        Badge = "POST-QUANTUM SECURITY"
        Title = "Google Titan M3 Protected"
        Subtitle = "Discrete hardware coprocessor & StrongBox KeyMint attestation"
    },
    @{
        Input = "F:\Intelligent-Search\screen_diagnostics.png"
        Filename = "07_Battery_RAM_Diagnostics.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 11, 9, 12)
        Accent = [System.Drawing.Color]::FromArgb(255, 255, 223, 158)
        Glow = [System.Drawing.Color]::FromArgb(255, 226, 142, 30)
        Badge = "REAL-TIME TELEMETRY"
        Title = "Battery & System Diagnostics"
        Subtitle = "Live wattage draw, battery health & RAM waveform graphs"
    },
    @{
        Input = "F:\Intelligent-Search\screen_settings.png"
        Filename = "08_Material_Settings_Hub.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 16, 13, 17)
        Accent = [System.Drawing.Color]::FromArgb(255, 180, 240, 150)
        Glow = [System.Drawing.Color]::FromArgb(255, 63, 163, 77)
        Badge = "INTUITIVE CONTROLS"
        Title = "Engineered for Power Users"
        Subtitle = "Modular preferences, backup vaults & default engine switching"
    }
)

foreach ($card in $cards) {
    Write-Host ("Rendering {0}..." -f $card.Filename)
    $dlPath = Join-Path $downloadsDir $card.Filename
    Render-PlayStoreCard `
        -inputImagePath $card.Input `
        -outputImagePath $dlPath `
        -statusBarBgColor $card.StatusBg `
        -accentColor $card.Accent `
        -ambientGlowColor $card.Glow `
        -badgeText $card.Badge `
        -titleText $card.Title `
        -subtitleText $card.Subtitle

    $repoPath = Join-Path $docsDir $card.Filename
    [System.IO.File]::Copy($dlPath, $repoPath, $true)
}

Write-Host "All 8 Google Play Store showcase screenshots have been generated and saved!"
