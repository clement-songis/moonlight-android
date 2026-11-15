{
  description = "Moonlight Android fork toolchain (AGP 9.4, Gradle 9.7.1, compileSdk 37, NDK 29)";
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/c508844df6c28fa6dabc1b6af70f3ccbd65c5201";
  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; config = { allowUnfree = true; android_sdk.accept_license = true; }; };
      sdk = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "37" ];
        buildToolsVersions = [ "36.0.0" "37.0.0" ];
        includeEmulator = false;
        includeSystemImages = false;
        includeNDK = true;
        ndkVersions = [ "29.0.14206865" ];
        cmdLineToolsVersion = "13.0";
      };
      home = "${sdk.androidsdk}/libexec/android-sdk";
    in {
      devShells.${system}.default = pkgs.mkShell {
        name = "moonlight-android";
        packages = [ pkgs.jdk21 pkgs.android-tools sdk.androidsdk ];
        ANDROID_HOME = home;
        ANDROID_SDK_ROOT = home;
        ANDROID_NDK_HOME = "${home}/ndk/29.0.14206865";
        JAVA_HOME = "${pkgs.jdk21}";
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${home}/build-tools/36.0.0/aapt2";
      };
    };
}
