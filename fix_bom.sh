sed -i 's/implementation(platform("androidx.compose:compose-bom:2024.02.00"))/implementation(platform("androidx.compose:compose-bom:2023.08.00"))/g' app/build.gradle.kts
sed -i 's/kotlinCompilerExtensionVersion = "1.5.10"/kotlinCompilerExtensionVersion = "1.5.1"/g' app/build.gradle.kts
