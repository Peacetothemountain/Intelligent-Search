
$path = $args[0]
(Get-Content $path) -replace '^pick', 'edit' | Set-Content $path

