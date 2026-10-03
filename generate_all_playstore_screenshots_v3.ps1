Add-Type -AssemblyName System.Drawing

function Create-RoundedRectanglePath {
    param([float]$x, [float]$y, [float]$width, [float]$height, [float]$radius)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $diameter = $radius * 2.0
    $arc = New-Object System.Drawing.RectangleF($x, $y, $diameter, $diameter)
    $path.AddArc($arc, 180, 90)
    $arc.X = $x + $width - $diameter
    $path.AddArc($arc, 270, 90)
    $arc.Y = $y + $height - $diameter
    $path.AddArc($arc, 0, 90)
    $arc.X = $x
    $path.AddArc($arc, 90, 90)
    $path.CloseFigure()
    return $path
}

function Clean-DeviceScreen {
    param(
        [System.Drawing.Bitmap]$srcBmp,
        [System.Drawing.Color]$statusBarBgColor,
        [bool]$cleanStatusBar = $true
    )
    
    $clean = New-Object System.Drawing.Bitmap($srcBmp.Width, $srcBmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($clean)
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    
    # Draw original image
    $g.DrawImage($srcBmp, 0, 0, $srcBmp.Width, $srcBmp.Height)
    
    if ($cleanStatusBar) {
        # Hardware status bar is 0..200px. Fill 0..196 with header background color
        $brush = New-Object System.Drawing.SolidBrush($statusBarBgColor)
        $g.FillRectangle($brush, 0, 0, $srcBmp.Width, 196)
        
        # Smooth 10px alpha blend from 196 to 206
        for ($i = 0; $i -lt 10; $i++) {
            $alpha = [int](255 * (1.0 - ($i / 10.0)))
            $blendColor = [System.Drawing.Color]::FromArgb($alpha, $statusBarBgColor.R, $statusBarBgColor.G, $statusBarBgColor.B)
            $blendBrush = New-Object System.Drawing.SolidBrush($blendColor)
            $g.FillRectangle($blendBrush, 0, (196 + $i), $srcBmp.Width, 1)
            $blendBrush.Dispose()
        }
        $brush.Dispose()
    }
    
    $g.Dispose()
    return $clean
}

function Render-PlayStoreCard {
    param(
        [string]$inputImagePath,
        [System.Drawing.Color]$statusBarBgColor,
        [bool]$cleanStatusBar,
        [System.Drawing.Color]$accentColor,
        [System.Drawing.Color]$ambientGlowColor,
        [string]$badgeText,
        [string]$titleText,
        [string]$subtitleText,
        [float]$scale
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
    
    # 1. Background gradient
    $bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Point(0, 0)),
        (New-Object System.Drawing.Point(0, $canvasH)),
        [System.Drawing.Color]::FromArgb(255, 14, 16, 22),
        [System.Drawing.Color]::FromArgb(255, 7, 8, 12)
    )
    $g.FillRectangle($bgBrush, 0, 0, $canvasW, $canvasH)
    $bgBrush.Dispose()
    
    # Ambient top glow behind Header & Phone
    $glowPath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $glowPath.AddEllipse(-200, -100, 1840, 1800)
    $pbg = New-Object System.Drawing.Drawing2D.PathGradientBrush($glowPath)
    $pbg.CenterPoint = New-Object System.Drawing.PointF(720, 480)
    $pbg.CenterColor = [System.Drawing.Color]::FromArgb(90, $ambientGlowColor.R, $ambientGlowColor.G, $ambientGlowColor.B)
    $pbg.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 7, 8, 12))
    $g.FillPath($pbg, $glowPath)
    $pbg.Dispose()
    $glowPath.Dispose()
    
    # Secondary subtle bottom glow
    $glowPath2 = New-Object System.Drawing.Drawing2D.GraphicsPath
    $glowPath2.AddEllipse(100, 1800, 1240, 1400)
    $pbg2 = New-Object System.Drawing.Drawing2D.PathGradientBrush($glowPath2)
    $pbg2.CenterPoint = New-Object System.Drawing.PointF(720, 2500)
    $pbg2.CenterColor = [System.Drawing.Color]::FromArgb(45, $ambientGlowColor.R, $ambientGlowColor.G, $ambientGlowColor.B)
    $pbg2.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 7, 8, 12))
    $g.FillPath($pbg2, $glowPath2)
    $pbg2.Dispose()
    $glowPath2.Dispose()
    
    # 2. Typography Header
    $badgeFont = New-Object System.Drawing.Font("Segoe UI", 16, [System.Drawing.FontStyle]::Bold)
    $titleFont = New-Object System.Drawing.Font("Segoe UI", 44, [System.Drawing.FontStyle]::Bold)
    $subFont = New-Object System.Drawing.Font("Segoe UI", 23, [System.Drawing.FontStyle]::Regular)
    
    $badgeSize = $g.MeasureString($badgeText, $badgeFont)
    $badgePadX = 36.0
    $badgePadY = 14.0
    $badgeW = $badgeSize.Width + ($badgePadX * 2.0)
    $badgeH = $badgeSize.Height + ($badgePadY * 2.0)
    $badgeX = (1440.0 - $badgeW) / 2.0
    $badgeY = 100.0
    
    $badgePath = Create-RoundedRectanglePath -x $badgeX -y $badgeY -width $badgeW -height $badgeH -radius ($badgeH / 2.0)
    $badgeBgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(40, $accentColor.R, $accentColor.G, $accentColor.B))
    $g.FillPath($badgeBgBrush, $badgePath)
    $badgeBgBrush.Dispose()
    
    $badgePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(110, $accentColor.R, $accentColor.G, $accentColor.B), 2.0)
    $g.DrawPath($badgePen, $badgePath)
    $badgePen.Dispose()
    $badgePath.Dispose()
    
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = [System.Drawing.StringAlignment]::Center
    $sf.LineAlignment = [System.Drawing.StringAlignment]::Center
    
    $badgeTextBrush = New-Object System.Drawing.SolidBrush($accentColor)
    $g.DrawString($badgeText, $badgeFont, $badgeTextBrush, (New-Object System.Drawing.RectangleF($badgeX, $badgeY, $badgeW, $badgeH)), $sf)
    $badgeTextBrush.Dispose()
    
    $titleBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 255, 255, 255))
    $titleRect = New-Object System.Drawing.RectangleF(60.0, 180.0, 1320.0, 110.0)
    $g.DrawString($titleText, $titleFont, $titleBrush, $titleRect, $sf)
    $titleBrush.Dispose()
    
    $subBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 195, 192, 204))
    $subRect = New-Object System.Drawing.RectangleF(60.0, 295.0, 1320.0, 80.0)
    $g.DrawString($subtitleText, $subFont, $subBrush, $subRect, $sf)
    $subBrush.Dispose()
    $sf.Dispose()
    
    # 3. Authentic Google Pixel 11 Pro XL Composite Geometry
    $screenW = 1080.0
    $screenH = 964.0 * $scale # 2399.0
    $screenX = 180.0
    $screenY = 520.0
    $screenRadius = 49.4 * $scale # 122.9 px
    
    $phoneX = $screenX - (19.0 * $scale) # 132.72
    $phoneY = $screenY - (20.0 * $scale) # 470.23
    $phoneW = 475.0 * $scale # 1182.0
    $phoneH = 1005.0 * $scale # 2501.0
    $phoneRadius = 65.0 * $scale # 161.75 px
    
    # Multi-layered ambient drop shadow behind chassis
    for ($step = 1; $step -le 5; $step++) {
        $sInflate = $step * 12.0
        $sOffsetY = $step * 10.0
        $sAlpha = [int](35 / $step)
        $shadowPath = Create-RoundedRectanglePath -x ($phoneX - $sInflate) -y ($phoneY - $sInflate + $sOffsetY) -width ($phoneW + ($sInflate * 2.0)) -height ($phoneH + ($sInflate * 2.0)) -radius ($phoneRadius + $sInflate)
        $sBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($sAlpha, 0, 0, 0))
        $g.FillPath($sBrush, $shadowPath)
        $sBrush.Dispose()
        $shadowPath.Dispose()
    }
    
    # 4. Clean and Draw the Raw App Screen with true Pixel 11 Pro XL corner radius
    $rawBmp = [System.Drawing.Bitmap]::FromFile($inputImagePath)
    $screenBmp = Clean-DeviceScreen -srcBmp $rawBmp -statusBarBgColor $statusBarBgColor -cleanStatusBar $cleanStatusBar
    $rawBmp.Dispose()
    
    $screenPath = Create-RoundedRectanglePath -x $screenX -y $screenY -width $screenW -height $screenH -radius $screenRadius
    $st = $g.Save()
    $g.SetClip($screenPath, [System.Drawing.Drawing2D.CombineMode]::Replace)
    $g.DrawImage($screenBmp, [float]$screenX, [float]$screenY, [float]$screenW, [float]$screenH)
    $g.Restore($st)
    $screenPath.Dispose()
    $screenBmp.Dispose()
    
    # 5. Draw authentic Pixel 11 Pro XL hollow frame over the screen
    # Draw a pristine, mathematically perfect vector frame instead of a blurry upscaled bitmap
    $frameThickness = 19.0 * $scale # ~47.3px
    $framePath = Create-RoundedRectanglePath -x $phoneX -y $phoneY -width $phoneW -height $phoneH -radius $phoneRadius
    
    # Outer metallic edge
    $metalPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 170, 175, 180), 3.0)
    $g.DrawPath($metalPen, $framePath)
    $metalPen.Dispose()
    
    # Inner bezel mask (draws a thick border from the outer edge to the screen edge)
    $bezelPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 12, 12, 12), $frameThickness)
    $bezelPen.Alignment = [System.Drawing.Drawing2D.PenAlignment]::Inset
    $g.DrawPath($bezelPen, $framePath)
    $bezelPen.Dispose()
    $framePath.Dispose()
    
    # 6. Draw authentic camera punch hole overlay (perfect vector circle)
    $camBoxW = 40.0 * $scale
    $camBoxH = 40.0 * $scale
    $camX = $phoneX + ($phoneW / 2.0) - ($camBoxW / 2.0)
    $camY = $phoneY + (19.0 * $scale) + (11.0 * $scale) # Bezel + top padding
    
    $camBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 8, 8, 8))
    $g.FillEllipse($camBrush, [float]$camX, [float]$camY, [float]$camBoxW, [float]$camBoxH)
    $camBrush.Dispose()
    
    # Tiny lens reflection
    $lensBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(40, 255, 255, 255))
    $g.FillEllipse($lensBrush, [float]($camX + ($camBoxW * 0.6)), [float]($camY + ($camBoxH * 0.2)), [float]($camBoxW * 0.2), [float]($camBoxH * 0.2))
    $lensBrush.Dispose()
    
    $badgeFont.Dispose()
    $titleFont.Dispose()
    $subFont.Dispose()
    $g.Dispose()
    
    return $canvas
}

# --- Main Execution Routine ---

$scale = 1080.0 / 434.0


# Target Directories
$downloadsRoot = "C:\Users\caref\Downloads"
$downloadsFolder = "C:\Users\caref\Downloads\PlayStore_Screenshots"
$docsFolder = "F:\Intelligent-Search\docs\images"

foreach ($dir in @($downloadsRoot, $downloadsFolder, $docsFolder)) {
    if (-not (Test-Path $dir)) {
        [System.IO.Directory]::CreateDirectory($dir) | Out-Null
    }
}

$cards = @(
    @{
        Input = "F:\Intelligent-Search\raw_screens\01_home_nokb.png"
        Filename = "01_Instant_Device_Search.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 17, 15, 21)
        CleanStatus = $true
        Accent = [System.Drawing.Color]::FromArgb(255, 208, 188, 255)
        Glow = [System.Drawing.Color]::FromArgb(255, 140, 90, 230)
        Badge = "INSTANT DEVICE SEARCH"
        Title = "Find Everything in Real Time"
        Subtitle = "Lightning-fast access to apps, contacts, files & shortcuts"
    },
    @{
        Input = "F:\Intelligent-Search\raw_screens\02_query_nokb.png"
        Filename = "02_Context_Aware_Search.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 17, 15, 21)
        CleanStatus = $true
        Accent = [System.Drawing.Color]::FromArgb(255, 168, 199, 250)
        Glow = [System.Drawing.Color]::FromArgb(255, 59, 120, 231)
        Badge = "INTELLIGENT RESULTS"
        Title = "Direct In-App Actions"
        Subtitle = "Top hits, navigation chips, Google Lens & web suggestions"
    },
    @{
        Input = "F:\Intelligent-Search\raw_screens\03_calc_nokb.png"
        Filename = "03_Builtin_Math_Engine.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 17, 15, 21)
        CleanStatus = $true
        Accent = [System.Drawing.Color]::FromArgb(255, 165, 242, 215)
        Glow = [System.Drawing.Color]::FromArgb(255, 29, 154, 122)
        Badge = "BUILT-IN MATH ENGINE"
        Title = "Instant Live Calculations"
        Subtitle = "Formulas, conversions, and one-tap copy directly in search"
    },
    @{
        Input = "F:\Intelligent-Search\raw_screens\04_widget.png"
        Filename = "04_Expressive_Home_Widgets.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 16, 13, 17)
        CleanStatus = $true
        Accent = [System.Drawing.Color]::FromArgb(255, 232, 180, 253)
        Glow = [System.Drawing.Color]::FromArgb(255, 156, 60, 231)
        Badge = "EXPRESSIVE HOME WIDGETS"
        Title = "Material You Widget Studio"
        Subtitle = "Dynamic system & Material search bars with live preview"
    },
    @{
        Input = "F:\Intelligent-Search\raw_screens\05_appearance.png"
        Filename = "05_Atmospheric_Themes.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 16, 13, 17)
        CleanStatus = $true
        Accent = [System.Drawing.Color]::FromArgb(255, 255, 179, 184)
        Glow = [System.Drawing.Color]::FromArgb(255, 217, 72, 98)
        Badge = "INFINITE CUSTOMIZATION"
        Title = "Atmospheric Themes & Blur"
        Subtitle = "Matrix rain animation, shape morphing & squiggly sliders"
    },
    @{
        Input = "F:\Intelligent-Search\raw_screens\06_security_broad.png"
        Filename = "06_Universal_Hardware_Security.png"
        LegacyFilename = "06_Hardware_Security_Titan_M3.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 11, 9, 12)
        CleanStatus = $false # Already clean custom compose dialog
        Accent = [System.Drawing.Color]::FromArgb(255, 146, 211, 245)
        Glow = [System.Drawing.Color]::FromArgb(255, 26, 136, 201)
        Badge = "CHIP-LEVEL ENCRYPTION"
        Title = "Universal Hardware Security"
        Subtitle = "Dedicated StrongBox & TEE defense across Titan, Knox Vault & Snapdragon"
    },
    @{
        Input = "F:\Intelligent-Search\raw_screens\07_diagnostics.png"
        Filename = "07_Battery_RAM_Diagnostics.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 11, 9, 12)
        CleanStatus = $true
        Accent = [System.Drawing.Color]::FromArgb(255, 255, 223, 158)
        Glow = [System.Drawing.Color]::FromArgb(255, 226, 142, 30)
        Badge = "REAL-TIME TELEMETRY"
        Title = "Battery & System Diagnostics"
        Subtitle = "Live wattage draw, battery health & RAM waveform graphs"
    },
    @{
        Input = "F:\Intelligent-Search\raw_screens\08_settings.png"
        Filename = "08_Material_Settings_Hub.png"
        StatusBg = [System.Drawing.Color]::FromArgb(255, 16, 13, 17)
        CleanStatus = $true
        Accent = [System.Drawing.Color]::FromArgb(255, 180, 240, 150)
        Glow = [System.Drawing.Color]::FromArgb(255, 63, 163, 77)
        Badge = "INTUITIVE CONTROLS"
        Title = "Engineered for Power Users"
        Subtitle = "Modular preferences, backup vaults & default engine switching"
    }
)

foreach ($card in $cards) {
    Write-Host ("Rendering {0}..." -f $card.Filename)
    $canvas = Render-PlayStoreCard `
        -inputImagePath $card.Input `
        -statusBarBgColor $card.StatusBg `
        -cleanStatusBar $card.CleanStatus `
        -accentColor $card.Accent `
        -ambientGlowColor $card.Glow `
        -badgeText $card.Badge `
        -titleText $card.Title `
        -subtitleText $card.Subtitle `
        -scale $scale

    # 1. Save directly to Downloads root
    $dlRootPath = Join-Path $downloadsRoot $card.Filename
    $canvas.Save($dlRootPath, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host ("Saved to Downloads root: {0}" -f $dlRootPath)

    # 2. Save to Downloads\PlayStore_Screenshots
    $dlSubPath = Join-Path $downloadsFolder $card.Filename
    $canvas.Save($dlSubPath, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host ("Saved to Downloads\PlayStore_Screenshots: {0}" -f $dlSubPath)

    # 3. Save to docs\images
    $docsPath = Join-Path $docsFolder $card.Filename
    $canvas.Save($docsPath, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host ("Saved to docs\images: {0}" -f $docsPath)

    # If slide 6, also overwrite legacy filename for full backward compatibility
    if ($card.LegacyFilename) {
        $legacyDocs = Join-Path $docsFolder $card.LegacyFilename
        $canvas.Save($legacyDocs, [System.Drawing.Imaging.ImageFormat]::Png)
        $legacyDl = Join-Path $downloadsRoot $card.LegacyFilename
        $canvas.Save($legacyDl, [System.Drawing.Imaging.ImageFormat]::Png)
        $legacySub = Join-Path $downloadsFolder $card.LegacyFilename
        $canvas.Save($legacySub, [System.Drawing.Imaging.ImageFormat]::Png)
    }

    $canvas.Dispose()
}

Write-Host "All 8 Pixel 11 Pro XL showcase screenshots successfully generated and placed in Downloads and docs!"
