package com.matochess.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * ItemStack을 Base64 문자열로 직렬화/역직렬화하는 유틸리티
 * NBT 데이터를 포함한 완전한 아이템 정보를 저장/불러올 수 있습니다
 */
public class ItemSerializer {

    /**
     * ItemStack을 Base64 문자열로 인코딩
     * @param item 인코딩할 아이템
     * @return Base64 문자열, 실패 시 null
     */
    public static String itemToBase64(ItemStack item) {
        if (item == null) {
            return null;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeObject(item);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Base64 문자열을 ItemStack으로 디코딩
     * @param base64 디코딩할 Base64 문자열
     * @return ItemStack, 실패 시 null
     */
    public static ItemStack itemFromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            return (ItemStack) dataInput.readObject();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 두 아이템이 NBT 포함 완전히 동일한지 확인
     * @param item1 첫 번째 아이템
     * @param item2 두 번째 아이템
     * @return 동일하면 true
     */
    public static boolean isSimilar(ItemStack item1, ItemStack item2) {
        if (item1 == null && item2 == null) {
            return true;
        }
        if (item1 == null || item2 == null) {
            return false;
        }

        // Bukkit의 isSimilar는 NBT를 포함한 비교를 수행합니다
        return item1.isSimilar(item2);
    }
}
