package io.github.karashilys.dailyrecipeshuffle.paper;

import io.github.karashilys.dailyrecipeshuffle.core.RecipeKind;
import java.util.Map;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.BrewingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.ComplexRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmithingTrimRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

/**
 * BukkitのレシピとコアのデータとのBukkit依存の変換をまとめる。
 */
final class BukkitRecipes {

    private BukkitRecipes() {}

    /**
     * Bukkitのレシピの種別を判定する。
     *
     * @param r レシピ
     * @return 種別
     */
    static RecipeKind kindOf(Recipe r) {
        // 装飾は ComplexRecipe も実装しているため、先に判定する
        if (r instanceof SmithingTrimRecipe) {
            return RecipeKind.SMITHING_TRIM;
        }
        // TransmuteRecipe(シュルカーボックスの染色など)も ComplexRecipe に含まれる
        if (r instanceof ComplexRecipe) {
            return RecipeKind.COMPLEX;
        }
        return switch (r) {
            case ShapedRecipe s -> RecipeKind.SHAPED;
            case ShapelessRecipe s -> RecipeKind.SHAPELESS;
            case FurnaceRecipe f -> RecipeKind.FURNACE;
            case BlastingRecipe b -> RecipeKind.BLASTING;
            case SmokingRecipe s -> RecipeKind.SMOKING;
            case CampfireRecipe c -> RecipeKind.CAMPFIRE;
            case StonecuttingRecipe s -> RecipeKind.STONECUTTING;
            case SmithingTransformRecipe s -> RecipeKind.SMITHING_TRANSFORM;
            case BrewingRecipe b -> RecipeKind.BREWING;
            default -> RecipeKind.UNKNOWN;
        };
    }

    /**
     * 完成品セットの識別文字列を作る。アイテム・個数・コンポーネントが同じなら同じ文字列になる。
     *
     * @param result 完成品
     * @return 識別文字列
     */
    static String resultId(ItemStack result) {
        String components = result.hasItemMeta() ? result.getItemMeta().getAsComponentString() : "";
        return result.getType().getKey() + " x" + result.getAmount() + components;
    }

    /**
     * 配置側のレシピから材料・調理時間・経験値などを引き継ぎ、完成品だけを差し替えたレシピを作る。
     * キーは元のレシピと同じ。
     *
     * @param base 配置側の元のレシピ
     * @param result 新しい完成品
     * @return 差し替えたレシピ
     * @throws IllegalArgumentException プール対象外の種別の場合
     */
    @SuppressWarnings("deprecation") // getChoiceMap のキーにnullの値が含まれ得るため明示的に扱う
    static Recipe rebuild(Recipe base, ItemStack result) {
        return switch (base) {
            case ShapedRecipe s -> {
                ShapedRecipe n = new ShapedRecipe(s.getKey(), result);
                n.shape(s.getShape());
                for (Map.Entry<Character, RecipeChoice> en : s.getChoiceMap().entrySet()) {
                    if (en.getValue() != null) {
                        n.setIngredient(en.getKey(), en.getValue());
                    }
                }
                n.setGroup(s.getGroup());
                n.setCategory(s.getCategory());
                yield n;
            }
            case ShapelessRecipe s -> {
                ShapelessRecipe n = new ShapelessRecipe(s.getKey(), result);
                for (RecipeChoice c : s.getChoiceList()) {
                    n.addIngredient(c);
                }
                n.setGroup(s.getGroup());
                n.setCategory(s.getCategory());
                yield n;
            }
            case CookingRecipe<?> c -> {
                // 調理時間と経験値は配置側に残す(仕様書5.2)
                CookingRecipe<?> n = switch (c) {
                    case FurnaceRecipe f -> new FurnaceRecipe(f.getKey(), result, f.getInputChoice(), f.getExperience(), f.getCookingTime());
                    case BlastingRecipe b -> new BlastingRecipe(b.getKey(), result, b.getInputChoice(), b.getExperience(), b.getCookingTime());
                    case SmokingRecipe s -> new SmokingRecipe(s.getKey(), result, s.getInputChoice(), s.getExperience(), s.getCookingTime());
                    case CampfireRecipe k -> new CampfireRecipe(k.getKey(), result, k.getInputChoice(), k.getExperience(), k.getCookingTime());
                    default -> throw new IllegalArgumentException("未対応のかまど系レシピ: " + c.getClass().getName());
                };
                n.setGroup(c.getGroup());
                n.setCategory(c.getCategory());
                yield n;
            }
            case StonecuttingRecipe s -> {
                StonecuttingRecipe n = new StonecuttingRecipe(s.getKey(), result, s.getInputChoice());
                n.setGroup(s.getGroup());
                yield n;
            }
            case SmithingTransformRecipe s -> new SmithingTransformRecipe(s.getKey(), result,
                    s.getTemplate(), s.getBase(), s.getAddition(), s.willCopyDataComponents());
            default -> throw new IllegalArgumentException("プール対象外のレシピ: " + base.getClass().getName());
        };
    }
}
