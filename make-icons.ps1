# Genera los íconos PNG de la app instalable (docs/icon-192.png y docs/icon-512.png)
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force (Join-Path $PSScriptRoot 'docs') | Out-Null

foreach ($size in 192, 512) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.Clear([System.Drawing.ColorTranslator]::FromHtml('#2B59E0'))

    $font = New-Object System.Drawing.Font 'Consolas', ([float]($size * 0.34)), ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = 'Center'
    $fmt.LineAlignment = 'Center'
    $rect = New-Object System.Drawing.RectangleF 0, 0, $size, $size
    $g.DrawString('{ }', $font, [System.Drawing.Brushes]::White, $rect, $fmt)

    $out = Join-Path $PSScriptRoot "docs\icon-$size.png"
    $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    "Creado $out"
}
