from pathlib import Path

catalog = Path('MyStudyCompanion/gradle/libs.versions.toml')
catalog_text = catalog.read_text(encoding='utf-8')
alias_line = 'androidx-wear-protolayout-material3 = { module = "androidx.wear.protolayout:protolayout-material3", version.ref = "wearProtoLayout" }'
if alias_line not in catalog_text:
    anchor = 'androidx-wear-protolayout-material = { module = "androidx.wear.protolayout:protolayout-material", version.ref = "wearProtoLayout" }'
    if anchor not in catalog_text:
        raise SystemExit('Wear ProtoLayout Material alias anchor was not found')
    catalog_text = catalog_text.replace(anchor, anchor + '\n' + alias_line)
    catalog.write_text(catalog_text, encoding='utf-8')

wear_build = Path('MyStudyCompanion/wear/build.gradle.kts')
wear_text = wear_build.read_text(encoding='utf-8')
dependency_line = '    implementation(libs.androidx.wear.protolayout.material3)'
if dependency_line not in wear_text:
    anchor = '    implementation(libs.androidx.wear.protolayout.material)'
    if anchor not in wear_text:
        raise SystemExit('Wear ProtoLayout Material dependency anchor was not found')
    wear_text = wear_text.replace(anchor, anchor + '\n' + dependency_line)
    wear_build.write_text(wear_text, encoding='utf-8')

print('Applied Wear ProtoLayout Material 3 compile dependency.')
