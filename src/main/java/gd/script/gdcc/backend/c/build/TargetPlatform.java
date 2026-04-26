package gd.script.gdcc.backend.c.build;

import gd.script.gdcc.enums.HardwareArchitecture;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum TargetPlatform {
    WINDOWS_X86_64(PlatformFamily.WINDOWS, HardwareArchitecture.X86_64, "x86_64-windows-msvc"),
    WINDOWS_AARCH64(PlatformFamily.WINDOWS, HardwareArchitecture.AARCH64, "aarch64-windows-msvc"),
    LINUX_X86_64(PlatformFamily.LINUX, HardwareArchitecture.X86_64, "x86_64-linux-gnu"),
    LINUX_AARCH64(PlatformFamily.LINUX, HardwareArchitecture.AARCH64, "aarch64-linux-gnu"),
    LINUX_RISCV64(PlatformFamily.LINUX, HardwareArchitecture.RISCV64, "riscv64-linux-gnu"),
    ANDROID_X86_64(PlatformFamily.ANDROID, HardwareArchitecture.X86_64, "x86_64-linux-android"),
    ANDROID_AARCH64(PlatformFamily.ANDROID, HardwareArchitecture.AARCH64, "aarch64-linux-android"),
    WEB_WASM32(PlatformFamily.WEB, HardwareArchitecture.WASM32, "wasm32-emscripten"),
    ;

    private final @NotNull PlatformFamily family;
    public final @NotNull HardwareArchitecture architecture;
    public final @NotNull String zigTarget;

    TargetPlatform(
            @NotNull PlatformFamily family,
            @NotNull HardwareArchitecture architecture,
            @NotNull String zigTarget
    ) {
        this.family = family;
        this.architecture = architecture;
        this.zigTarget = zigTarget;
    }

    public boolean isWindows() {
        return family == PlatformFamily.WINDOWS;
    }

    public @NotNull String sharedLibraryFileName(@NotNull String outputBaseName) {
        return switch (family) {
            case WINDOWS -> outputBaseName + ".dll";
            case LINUX, ANDROID -> "lib" + outputBaseName + ".so";
            case WEB -> outputBaseName + ".wasm";
        };
    }

    public static @NotNull TargetPlatform getNativePlatform() {
        var osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        var osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        var javaVendor = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT);
        var vmName = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);
        var isAndroidRuntime = osName.contains("android")
                || javaVendor.contains("android")
                || vmName.contains("dalvik")
                || vmName.contains("art");
        var arch = parseArchitecture(osArch);
        if (isAndroidRuntime) {
            return switch (arch) {
                case X86_64 -> ANDROID_X86_64;
                case AARCH64 -> ANDROID_AARCH64;
                case RISCV64, WASM32 -> throw unsupportedNativePlatform(osName, osArch);
            };
        }
        return switch (osName) {
            case String s when s.contains("win") -> switch (arch) {
                case X86_64 -> WINDOWS_X86_64;
                case AARCH64 -> WINDOWS_AARCH64;
                case RISCV64, WASM32 -> throw unsupportedNativePlatform(osName, osArch);
            };
            case String s when s.contains("linux") -> switch (arch) {
                case X86_64 -> LINUX_X86_64;
                case AARCH64 -> LINUX_AARCH64;
                case RISCV64 -> LINUX_RISCV64;
                case WASM32 -> throw unsupportedNativePlatform(osName, osArch);
            };
            default -> throw unsupportedNativePlatform(osName, osArch);
        };
    }

    private static @NotNull HardwareArchitecture parseArchitecture(@NotNull String osArch) {
        return switch (osArch) {
            case "x86_64", "amd64", "x64" -> HardwareArchitecture.X86_64;
            case "aarch64", "arm64" -> HardwareArchitecture.AARCH64;
            case "riscv64" -> HardwareArchitecture.RISCV64;
            case "wasm32" -> HardwareArchitecture.WASM32;
            default -> throw unsupportedNativePlatform(System.getProperty("os.name", ""), osArch);
        };
    }

    private static @NotNull IllegalStateException unsupportedNativePlatform(@NotNull String osName, @NotNull String osArch) {
        return new IllegalStateException("Unsupported native platform: os.name='" + osName + "', os.arch='" + osArch + "'");
    }

    private enum PlatformFamily {
        WINDOWS,
        LINUX,
        ANDROID,
        WEB,
    }
}
