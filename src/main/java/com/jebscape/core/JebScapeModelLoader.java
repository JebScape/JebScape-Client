/*
 * Copyright (c) 2023, Justin Ead (Jebrim) <jebscapeplugin@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jebscape.core;

import net.runelite.api.*;
import net.runelite.api.kit.*;

public class JebScapeModelLoader
{
	private enum BodyPart
	{
		HAIR,
		JAW,
		TORSO,
		ARMS,
		HANDS,
		LEGS,
		FEET
	}
	
	private static final short[] BODY_COLOURS_1_SOURCE = new short[]{
			6798, 8741, 25238, 4626, 4550
	};
	private static final short[][] BODY_COLOURS_1_DEST = new short[][]{
			{6798, 107, 10283, 16, 4797, 7744, 5799, 4634, -31839, 22433, 2983, -11343, 8, 5281, 10438, 3650, -27322, -21845, 200, 571, 908, 21830, 28946, -15701, -14010, -22122, 937, 8130, -13422, 30385},
			{8741, 12, -1506, -22374, 7735, 8404, 1701, -27106, 24094, 10153, -8915, 4783, 1341, 16578, -30533, 25239, 8, 5281, 10438, 3650, -27322, -21845, 200, 571, 908, 21830, 28946, -15701, -14010},
			{25238, 8742, 12, -1506, -22374, 7735, 8404, 1701, -27106, 24094, 10153, -8915, 4783, 1341, 16578, -30533, 8, 5281, 10438, 3650, -27322, -21845, 200, 571, 908, 21830, 28946, -15701, -14010},
			{4626, 11146, 6439, 12, 4758, 10270},
			{4550, 4537, 5681, 5673, 5790, 6806, 8076, 4574, 17050, 0, 127, -31821, -17991}
	};
	private static final short[] BODY_COLOURS_2_SOURCE = new short[]{
			-10304, 9104, -1, -1, -1
	};
	private static final short[][] BODY_COLOURS_2_DEST = new short[][]{
			{6554, 115, 10304, 28, 5702, 7756, 5681, 4510, -31835, 22437, 2859, -11339, 16, 5157, 10446, 3658, -27314, -21965, 472, 580, 784, 21966, 28950, -15697, -14002, -22116, 945, 8144, -13414, 30389},
			{9104, 10275, 7595, 3610, 7975, 8526, 918, -26734, 24466, 10145, -6882, 5027, 1457, 16565, -30545, 25486, 24, 5392, 10429, 3673, -27335, -21957, 192, 687, 412, 21821, 28835, -15460, -14019},
			new short[0],
			new short[0],
			new short[0]
	};
	
	private Client client;
	private IndexDataBase gameDB;
	
	private static final int GHOST_COLOR = 18626;
	private static final int GHOST_TRANSPARENCY = 32;
	
	private static final int KIT_CONFIG_TYPE = 3;
	private RuneLiteKitLoader kitLoader = new RuneLiteKitLoader();
	
	private static final int ITEM_CONFIG_TYPE = 10;
	private RuneLiteItemLoader itemLoader = new RuneLiteItemLoader();
	
	private static final int NUM_MODEL_DATA = 36;
	private ModelData[] modelData = new ModelData[NUM_MODEL_DATA];
	private int[] modelIDs = new int[NUM_MODEL_DATA];
	
	public void init(Client client)
	{
		this.client = client;
		this.gameDB = client.getIndexConfig();
	}
	
	private static ModelData recolourKitModel(ModelData modelData, int bodyPart, int[] kitRecolours)
	{
		/*
		0 = Hair, Jaw
		1 = Torso, Arms
		2 = Legs
		3 = Boots
		4 = Hands, Other
		*/
		switch (bodyPart)
		{
			case 0:
				modelData.recolor(BODY_COLOURS_1_SOURCE[0], BODY_COLOURS_1_DEST[0][kitRecolours[0]]);
				modelData.recolor(BODY_COLOURS_2_SOURCE[0], BODY_COLOURS_2_DEST[0][kitRecolours[0]]);
				modelData.recolor(BODY_COLOURS_1_SOURCE[4], BODY_COLOURS_1_DEST[4][kitRecolours[4]]);
				break;
			case 1:
				modelData.recolor(BODY_COLOURS_1_SOURCE[1], BODY_COLOURS_1_DEST[1][kitRecolours[1]]);
				modelData.recolor(BODY_COLOURS_2_SOURCE[1], BODY_COLOURS_2_DEST[1][kitRecolours[1]]);
				modelData.recolor(BODY_COLOURS_1_SOURCE[4], BODY_COLOURS_1_DEST[4][kitRecolours[4]]);
				break;
			case 2:
				modelData.recolor(BODY_COLOURS_1_SOURCE[2], BODY_COLOURS_1_DEST[2][kitRecolours[2]]);
				modelData.recolor(BODY_COLOURS_1_SOURCE[3], BODY_COLOURS_1_DEST[3][kitRecolours[3]]);
				modelData.recolor(BODY_COLOURS_1_SOURCE[4], BODY_COLOURS_1_DEST[4][kitRecolours[4]]);
				break;
			case 3:
				modelData.recolor(BODY_COLOURS_1_SOURCE[3], BODY_COLOURS_1_DEST[3][kitRecolours[3]]);
				modelData.recolor(BODY_COLOURS_1_SOURCE[4], BODY_COLOURS_1_DEST[4][kitRecolours[4]]);
				break;
			case 4:
				modelData.recolor(BODY_COLOURS_1_SOURCE[4], BODY_COLOURS_1_DEST[4][kitRecolours[4]]);
		}
		
		return modelData;
	}
	
	public Model loadPlayerCloneRenderable()
	{
		Player player = client.getLocalPlayer();
		PlayerComposition playerComposition = player.getPlayerComposition();
		int gender = playerComposition.getGender();
		int[] equipmentIds = playerComposition.getEquipmentIds();
		
		int numModelIDs = 0;
		
		int[] colorIDs = playerComposition.getColors();
		ColorTextureOverride[] overrides = playerComposition.getColorTextureOverrides();
		
		for (int i = 0; i < equipmentIds.length; i++)
		{
			if (equipmentIds[i] > 512)
			{
				int itemID = equipmentIds[i] - 512;
				byte[] itemData = gameDB.loadData(ITEM_CONFIG_TYPE, itemID);
				RuneLiteItemDefinition itemDefinition = itemLoader.load(itemID, itemData);
				int startingCount = numModelIDs;
				if (gender == 0)
				{
					if (itemDefinition.maleModel0 >= 0)
						modelIDs[numModelIDs++] = itemDefinition.maleModel0;
					if (itemDefinition.maleModel1 >= 0)
						modelIDs[numModelIDs++] = itemDefinition.maleModel1;
					if (itemDefinition.maleModel2 >= 0)
						modelIDs[numModelIDs++] = itemDefinition.maleModel2;
				}
				else if (gender == 1)
				{
					if (itemDefinition.femaleModel0 >= 0)
						modelIDs[numModelIDs++] = itemDefinition.femaleModel0;
					if (itemDefinition.femaleModel1 >= 0)
						modelIDs[numModelIDs++] = itemDefinition.femaleModel1;
					if (itemDefinition.femaleModel2 >= 0)
						modelIDs[numModelIDs++] = itemDefinition.femaleModel2;
				}
				
				for (int modelIndex = startingCount; modelIndex < numModelIDs; modelIndex++)
				{
					modelData[modelIndex] = client.loadModelData(modelIDs[modelIndex]);
					if (itemDefinition.colorFind != null)
					{
						int numToRecolor = itemDefinition.colorFind.length;
						for (int recolorIndex = 0; recolorIndex < numToRecolor; recolorIndex++)
						{
							short replaceColor = itemDefinition.colorReplace[recolorIndex];
							if (overrides != null && overrides.length >= i && overrides[i].getColorToReplaceWith() != null && overrides[i].getColorToReplaceWith().length >= 0)
							{
								replaceColor = overrides[i].getColorToReplaceWith()[recolorIndex];
							}
							modelData[modelIndex].recolor(itemDefinition.colorFind[recolorIndex], replaceColor);
						}
					}
				}
			}
			else if (equipmentIds[i] >= 256)
			{
				int kitID = equipmentIds[i] - 256;
				byte[] kitData = gameDB.loadData(KIT_CONFIG_TYPE, kitID);
				RuneLiteKitDefinition kitDefinition = kitLoader.load(kitID, kitData);
				for (int j = 0; j < kitDefinition.models.length; j++)
				{
					modelIDs[numModelIDs] = kitDefinition.models[j];
					modelData[numModelIDs] = client.loadModelData(modelIDs[numModelIDs]);
					
					if (kitDefinition.recolorToFind != null)
					{
						int numToRecolor = kitDefinition.recolorToFind.length;
						for (int recolorIndex = 0; recolorIndex < numToRecolor; recolorIndex++)
						{
							modelData[numModelIDs].recolor(kitDefinition.recolorToFind[recolorIndex], kitDefinition.recolorToReplace[recolorIndex]);
						}
					}
					
					final int[] bodyPartMap = { 0, 0, 1, 1, 4, 2, 3 };
					if (kitDefinition.bodyPartId >= 0)
					{
						recolourKitModel(modelData[numModelIDs], bodyPartMap[kitDefinition.bodyPartId], colorIDs);
					}
					
					numModelIDs++;
				}
			}
		}
		
		ModelData combinedModelData = client.mergeModels(modelData, numModelIDs);
		
		// use the same lighting parameters used for NPCs by Jagex
		// TODO: This is a bit bright compared to what the actual players look like; tweak dimmer
		return combinedModelData.light(64, 850, -30, -50, -30);
	}
	
	private static int[] skillcapeIDs = new int[]
	{
			22284,	// default ghost cape
			18917,	// AGILITY
			18919, 	// ATTACK
			18921,	// FARMING
			18922,	// CONSTRUCTION
			18923,	// COOKING
			18926,	// CRAFTING
			18927,	// DEFENSE
			18930,	// RANGED
			18931,	// FIREMAKING
			18933,	// FISHING
			18936,	// HERBLORE
			18937,	// FLETCHING
			18941,	// MAGIC
			18944,	// PRAYER
			18947,	// RUNECRAFT
			18949,	// SLAYER
			18952,	// STRENGTH
			18957,	// WOODCUTTING
			18958,	// THIEVING
			18979,	// HITPOINTS
			19099,	// MINING
			19100,	// SMITHING
			19873,	// HUNTER
			29617,	// MAX CAPE BUCKET
			29616,	// MAX CAPE MALE
			29624	// MAX CAPE FEMALE
	};
	
	public Model loadPlayerGhostRenderable()
	{
		Player player = client.getLocalPlayer();
		PlayerComposition playerComposition = player.getPlayerComposition();
		int gender = playerComposition.getGender();
		int[] equipmentIds = playerComposition.getEquipmentIds();
		
		int numModelIDs = 0;
		
		for (int i = 0; i < equipmentIds.length; i++)
		{
			if (i == KitType.CAPE.ordinal())
			{
				// skip
				continue;
			}
			else if (i == KitType.HEAD.ordinal() || i == KitType.HAIR.ordinal() || i == KitType.JAW.ordinal())
			{
				int kitID = playerComposition.getKitId(KitType.values()[i]);
				if (kitID >= 0)
				{
					byte[] kitData = gameDB.loadData(KIT_CONFIG_TYPE, kitID);
					RuneLiteKitDefinition kitDefinition = kitLoader.load(kitID, kitData);
					for (int j = 0; j < kitDefinition.models.length; j++)
					{
						modelIDs[numModelIDs++] = kitDefinition.models[j];
					}
				}
			}
			else if (equipmentIds[i] > 512)
			{
				int itemID = equipmentIds[i] - 512;
				byte[] itemData = gameDB.loadData(ITEM_CONFIG_TYPE, itemID);
				RuneLiteItemDefinition itemDefinition = itemLoader.load(itemID, itemData);
				if (gender == 0)
				{
					if (itemDefinition.maleModel0 >= 0)
						modelIDs[numModelIDs++] = itemDefinition.maleModel0;
					if (itemDefinition.maleModel1 >= 0)
						modelIDs[numModelIDs++] = itemDefinition.maleModel1;
					if (itemDefinition.maleModel2 >= 0)
						modelIDs[numModelIDs++] = itemDefinition.maleModel2;
				}
				else if (gender == 1)
				{
					if (itemDefinition.femaleModel0 >= 0)
						modelIDs[numModelIDs++] = itemDefinition.femaleModel0;
					if (itemDefinition.femaleModel1 >= 0)
						modelIDs[numModelIDs++] = itemDefinition.femaleModel1;
					if (itemDefinition.femaleModel2 >= 0)
						modelIDs[numModelIDs++] = itemDefinition.femaleModel2;
				}
			}
			else if (equipmentIds[i] >= 256)
			{
				int kitID = equipmentIds[i] - 256;
				byte[] kitData = gameDB.loadData(KIT_CONFIG_TYPE, kitID);
				RuneLiteKitDefinition kitDefinition = kitLoader.load(kitID, kitData);
				for (int j = 0; j < kitDefinition.models.length; j++)
				{
					modelIDs[numModelIDs++] = kitDefinition.models[j];
				}
			}
		}
		
		// load ghost model
		//ModelData ghostModelData = client.loadModelData(client.getNpcDefinition(GHOST_3516).getModels()[0]);
		short newColor = GHOST_COLOR;//ghostModelData.getFaceColors()[0]; // 18626
		byte newTransparency = GHOST_TRANSPARENCY;//ghostModelData.getFaceTransparencies()[0];
		
		modelIDs[numModelIDs++] = 29616;//skillcapeIDs[1]; // load cape separately
		for (int i = 0; i < numModelIDs; i++)
		{
			modelData[i] = client.loadModelData(modelIDs[i]);
		}
		int numCapeFaces = modelData[numModelIDs - 1].getFaceColors().length;
		
		ModelData combinedModelData = client.mergeModels(modelData, numModelIDs);
		ModelData clonedModelData = combinedModelData.cloneColors();
		if (clonedModelData.getFaceTransparencies() != null)
			clonedModelData = clonedModelData.cloneTransparencies();
		short[] clonedColors = clonedModelData.getFaceColors();
		int numToReplace = clonedColors.length - numCapeFaces;
		for (int i = 0; i < numToReplace; i++)
		{
			clonedColors[i] = newColor;
		}
		
		if (clonedModelData.getFaceTransparencies() != null)
		{
			for (int i = 0; i < clonedModelData.getFaceTransparencies().length; i++)
			{
				if (clonedModelData.getFaceTransparencies()[i] == 0)
					clonedModelData.getFaceTransparencies()[i] = 60;//48;//newTransparency;)
			}
		}
		
		// use the same lighting parameters used for NPCs by Jagex
		return clonedModelData.light(64, 850, -30, -50, -30);
	}
}
