package ru.playtimegate.tab;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Function;

/**
 * Привязка к TAB через рефлексию: в байткоде нет классов me.neznamy,
 * поэтому плагин не привязан к конкретной версии TAB (6.x — основной
 * поддерживаемый, старые API берутся как fallback).
 *
 * <p>При любом сбое мост просто не поднимается: трекер AFK продолжает
 * работать, только префикс в табе не отображается.
 */
public final class TabAfkBridge {

    private static final String TAB_API_CLASS = "me.neznamy.tab.api.TabAPI";

    private final Object api;
    private final Object placeholder;
    private final Method updateValue;
    private final Method getPlayer;

    private TabAfkBridge(Object api, Object placeholder, Method updateValue, Method getPlayer) {
        this.api = api;
        this.placeholder = placeholder;
        this.updateValue = updateValue;
        this.getPlayer = getPlayer;
    }

    /**
     * Поднимает мост и регистрирует плейсхолдер.
     *
     * @param valueOf функция «UUID игрока → значение плейсхолдера», её вызывает TAB
     * @return мост либо null, если TAB несовместим или не запущен
     */
    public static TabAfkBridge create(JavaPlugin plugin, String identifier, int refreshMs,
                                      Function<UUID, String> valueOf) {
        try {
            Class<?> apiClass = Class.forName(TAB_API_CLASS);
            Object api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) {
                return null;
            }
            Object manager = api.getClass().getMethod("getPlaceholderManager").invoke(api);
            Method register = findRegisterMethod(manager);
            if (register == null) {
                plugin.getLogger().warning(
                        "TAB: метод registerPlayerPlaceholder не найден — версия TAB "
                                + "не поддерживается, AFK-префикс в табе отключён.");
                return null;
            }
            Function<Object, String> function = tabPlayer -> {
                UUID id = uniqueId(tabPlayer);
                return id != null ? valueOf.apply(id) : "";
            };
            Class<?>[] params = register.getParameterTypes();
            Object placeholder = params.length == 3
                    ? register.invoke(manager, identifier, refreshMs, function)
                    : register.invoke(manager, identifier, function);
            // updateValue может быть null (неточечное обновление отключается,
            // работает авто-рефреш TAB).
            return new TabAfkBridge(api, placeholder, findUpdateValueMethod(placeholder),
                    getPlayerMethod(api));
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось привязаться к TAB (AFK-префикс отключён): " + t);
            return null;
        }
    }

    /**
     * Точечное обновление значения для игрока (мгновенно, не дожидаясь
     * авто-рефреша TAB). Ошибки игнорируются — авто-рефреш подхватит.
     */
    public void update(Player player, String value) {
        if (updateValue == null) {
            return;
        }
        try {
            Object tabPlayer = getPlayer.invoke(api, player.getUniqueId());
            if (tabPlayer != null) {
                updateValue.invoke(placeholder, tabPlayer, value);
            }
        } catch (Throwable ignored) {
            // намеренно пусто
        }
    }

    private static Method findRegisterMethod(Object manager) {
        Method fallback = null;
        for (Method method : manager.getClass().getMethods()) {
            if (!"registerPlayerPlaceholder".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 3
                    && params[0] == String.class
                    && params[1] == int.class
                    && params[2] == Function.class) {
                return method; // TAB 6.x: интервал авто-рефреша явно
            }
            if (params.length == 2
                    && params[0] == String.class
                    && params[1] == Function.class) {
                fallback = method; // старое API: интервал берёт сам TAB
            }
        }
        return fallback;
    }

    private static Method findUpdateValueMethod(Object placeholder) {
        for (Method method : placeholder.getClass().getMethods()) {
            if ("updateValue".equals(method.getName())
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[1] == String.class) {
                return method;
            }
        }
        return null;
    }

    private static Method getPlayerMethod(Object api) throws NoSuchMethodException {
        return api.getClass().getMethod("getPlayer", UUID.class);
    }

    private static UUID uniqueId(Object tabPlayer) {
        try {
            Object id = tabPlayer.getClass().getMethod("getUniqueId").invoke(tabPlayer);
            return id instanceof UUID ? (UUID) id : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
