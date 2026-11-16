package net.azisaba.craftgui.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

abstract class BaseGuiHolder : InventoryHolder {

    private var holderInventory: Inventory? = null

    fun setInventory(inventory: Inventory) {
        holderInventory = inventory
    }

    override fun getInventory(): Inventory =
        checkNotNull(holderInventory) { "GUI inventory has not been initialized yet" }
}

class CraftGuiHolder(val page: Int) : BaseGuiHolder()

class EditGuiHolder(val page: Int) : BaseGuiHolder()

class RegisterGuiHolder(val page: Int, val slot: Int, val recipeId: String) : BaseGuiHolder()
