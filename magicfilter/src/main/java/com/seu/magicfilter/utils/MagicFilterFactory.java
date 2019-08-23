package com.seu.magicfilter.utils;

import com.seu.magicfilter.advanced.MagicAmaroFilter;
import com.seu.magicfilter.advanced.MagicAntiqueFilter;
import com.seu.magicfilter.advanced.MagicBeautyFilter;
import com.seu.magicfilter.advanced.MagicBlackCatFilter;
import com.seu.magicfilter.advanced.MagicBrannanFilter;
import com.seu.magicfilter.advanced.MagicBrooklynFilter;
import com.seu.magicfilter.advanced.MagicCalmFilter;
import com.seu.magicfilter.advanced.MagicCoolFilter;
import com.seu.magicfilter.advanced.MagicEarlyBirdFilter;
import com.seu.magicfilter.advanced.MagicEmeraldFilter;
import com.seu.magicfilter.advanced.MagicEvergreenFilter;
import com.seu.magicfilter.advanced.MagicFreudFilter;
import com.seu.magicfilter.advanced.MagicHealthyFilter;
import com.seu.magicfilter.advanced.MagicHudsonFilter;
import com.seu.magicfilter.advanced.MagicInkwellFilter;
import com.seu.magicfilter.advanced.MagicKevinFilter;
import com.seu.magicfilter.advanced.MagicLatteFilter;
import com.seu.magicfilter.advanced.MagicN1977Filter;
import com.seu.magicfilter.advanced.MagicNashvilleFilter;
import com.seu.magicfilter.advanced.MagicNostalgiaFilter;
import com.seu.magicfilter.advanced.MagicPixarFilter;
import com.seu.magicfilter.advanced.MagicRiseFilter;
import com.seu.magicfilter.advanced.MagicRomanceFilter;
import com.seu.magicfilter.advanced.MagicSakuraFilter;
import com.seu.magicfilter.advanced.MagicSierraFilter;
import com.seu.magicfilter.advanced.MagicSketchFilter;
import com.seu.magicfilter.advanced.MagicSkinWhitenFilter;
import com.seu.magicfilter.advanced.MagicSunriseFilter;
import com.seu.magicfilter.advanced.MagicSunsetFilter;
import com.seu.magicfilter.advanced.MagicSutroFilter;
import com.seu.magicfilter.advanced.MagicTenderFilter;
import com.seu.magicfilter.advanced.MagicToasterFilter;
import com.seu.magicfilter.advanced.MagicValenciaFilter;
import com.seu.magicfilter.advanced.MagicWaldenFilter;
import com.seu.magicfilter.advanced.MagicWarmFilter;
import com.seu.magicfilter.advanced.MagicWhiteCatFilter;
import com.seu.magicfilter.advanced.MagicXproIIFilter;
import com.seu.magicfilter.base.gpuimage.GPUImageFilter;

public class MagicFilterFactory {

    public static GPUImageFilter initFilters(MagicFilterType type) {
        if (type == MagicFilterType.NONE) {
            return new GPUImageFilter();
        } else if (type == MagicFilterType.WHITECAT) {
            return new MagicWhiteCatFilter();
        } else if (type == MagicFilterType.BLACKCAT) {
            return new MagicBlackCatFilter();
        } else if (type == MagicFilterType.SKINWHITEN) {
            return new MagicSkinWhitenFilter();
        } else if (type == MagicFilterType.BEAUTY) {
            return new MagicBeautyFilter();
        } else if (type == MagicFilterType.ROMANCE) {
            return new MagicRomanceFilter();
        } else if (type == MagicFilterType.SAKURA) {
            return new MagicSakuraFilter();
        } else if (type == MagicFilterType.AMARO) {
            return new MagicAmaroFilter();
        } else if (type == MagicFilterType.WALDEN) {
            return new MagicWaldenFilter();
        } else if (type == MagicFilterType.ANTIQUE) {
            return new MagicAntiqueFilter();
        } else if (type == MagicFilterType.CALM) {
            return new MagicCalmFilter();
        } else if (type == MagicFilterType.BRANNAN) {
            return new MagicBrannanFilter();
        } else if (type == MagicFilterType.BROOKLYN) {
            return new MagicBrooklynFilter();
        } else if (type == MagicFilterType.EARLYBIRD) {
            return new MagicEarlyBirdFilter();
        } else if (type == MagicFilterType.FREUD) {
            return new MagicFreudFilter();
        } else if (type == MagicFilterType.HUDSON) {
            return new MagicHudsonFilter();
        } else if (type == MagicFilterType.INKWELL) {
            return new MagicInkwellFilter();
        } else if (type == MagicFilterType.KEVIN) {
            return new MagicKevinFilter();
        } else if (type == MagicFilterType.N1977) {
            return new MagicN1977Filter();
        } else if (type == MagicFilterType.NASHVILLE) {
            return new MagicNashvilleFilter();
        } else if (type == MagicFilterType.PIXAR) {
            return new MagicPixarFilter();
        } else if (type == MagicFilterType.RISE) {
            return new MagicRiseFilter();
        } else if (type == MagicFilterType.SIERRA) {
            return new MagicSierraFilter();
        } else if (type == MagicFilterType.SUTRO) {
            return new MagicSutroFilter();
        } else if (type == MagicFilterType.TOASTER2) {
            return new MagicToasterFilter();
        } else if (type == MagicFilterType.VALENCIA) {
            return new MagicValenciaFilter();
        } else if (type == MagicFilterType.XPROII) {
            return new MagicXproIIFilter();
        } else if (type == MagicFilterType.EVERGREEN) {
            return new MagicEvergreenFilter();
        } else if (type == MagicFilterType.HEALTHY) {
            return new MagicHealthyFilter();
        } else if (type == MagicFilterType.COOL) {
            return new MagicCoolFilter();
        } else if (type == MagicFilterType.EMERALD) {
            return new MagicEmeraldFilter();
        } else if (type == MagicFilterType.LATTE) {
            return new MagicLatteFilter();
        } else if (type == MagicFilterType.WARM) {
            return new MagicWarmFilter();
        } else if (type == MagicFilterType.TENDER) {
            return new MagicTenderFilter();
        } else if (type == MagicFilterType.NOSTALGIA) {
            return new MagicNostalgiaFilter();
        } else if (type == MagicFilterType.SUNRISE) {
            return new MagicSunriseFilter();
        } else if (type == MagicFilterType.SUNSET) {
            return new MagicSunsetFilter();
        } else if (type == MagicFilterType.SKETCH) {
            return new MagicSketchFilter();
        } else {
            return null;
        }
    }
}
