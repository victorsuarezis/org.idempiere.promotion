/******************************************************************************
 * Copyright (C) 2009 Low Heng Sin                                            *
 * Copyright (C) 2009 Idalica Corporation                                     *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *****************************************************************************/
package org.idempiere.model;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.PO;
import org.compiere.util.DB;
import org.osgi.service.event.Event;

/**
 * 
 * @author hengsin
 * @contributor: <a href="mailto:victor.suarez.is@gmail.com">Ing. Victor Suarez</a>
 * 
 */
public class PromotionValidator extends AbstractEventHandler {
	
	@Override
	protected void initialize() {
		registerTableEvent(IEventTopics.PO_AFTER_DELETE, MOrderLine.Table_Name);
		registerTableEvent(IEventTopics.DOC_AFTER_PREPARE, MOrder.Table_Name);
	}
	
	@Override
	protected void doHandleEvent(Event event) {
		PO po = getPO(event);
		String type = event.getTopic();
		
		if (po instanceof MOrder ) {
			MOrder order = (MOrder) po;
			if (IEventTopics.DOC_AFTER_PREPARE.equals(type)) {
				try {
					PromotionRule.applyPromotions(order);
					order.getLines(true, null);
					order.calculateTaxTotal();
					order.saveEx();
					increasePromotionCounter(order);
				} catch (Exception e) {
					if (e instanceof RuntimeException)
						throw (RuntimeException)e;
					else
						throw new AdempiereException(e.getLocalizedMessage(), e);
				}
			} else if (IEventTopics.DOC_AFTER_VOID.equals(type)) {
				decreasePromotionCounter(order);
			}
		} else if(po instanceof MOrderLine) {
			MOrderLine orderLine = (MOrderLine) po;
			if(IEventTopics.PO_AFTER_DELETE.equals(type)) {
				MOrder order = orderLine.getParent();
				String promotionCode = (String)order.get_Value("PromotionCode");
				if (orderLine.getC_Charge_ID() > 0) {
					Integer promotionID = (Integer) orderLine.get_Value("M_Promotion_ID");
					if (promotionID != null && promotionID.intValue() > 0) {
						int M_PromotionPreCondition_ID = findPromotionPreConditionId(
								order, promotionCode, promotionID);
						if (M_PromotionPreCondition_ID > 0) {
							String update = "UPDATE M_PromotionPreCondition SET PromotionCounter = PromotionCounter - 1 WHERE M_PromotionPreCondition_ID = ?";
							DB.executeUpdate(update, M_PromotionPreCondition_ID, order.get_TrxName());
						}
					}
				}
			}
		}
	}

	private void increasePromotionCounter(MOrder order) {
		MOrderLine[] lines = order.getLines(false, null);
		String promotionCode = (String)order.get_Value("PromotionCode");
		for (MOrderLine ol : lines) {
			if (ol.getC_Charge_ID() > 0) {
				Integer promotionID = (Integer) ol.get_Value("M_Promotion_ID");
				if (promotionID != null && promotionID.intValue() > 0) {

					int M_PromotionPreCondition_ID = findPromotionPreConditionId(
							order, promotionCode, promotionID);
					if (M_PromotionPreCondition_ID > 0) {
						String update = "UPDATE M_PromotionPreCondition SET PromotionCounter = PromotionCounter + 1 WHERE M_PromotionPreCondition_ID = ?";
						DB.executeUpdate(update, M_PromotionPreCondition_ID, order.get_TrxName());
					}
				}
			}
		}
	}

	private void decreasePromotionCounter(MOrder order) {
		MOrderLine[] lines = order.getLines(false, null);
		String promotionCode = (String)order.get_Value("PromotionCode");
		for (MOrderLine ol : lines) {
			if (ol.getC_Charge_ID() > 0) {
				Integer promotionID = (Integer) ol.get_Value("M_Promotion_ID");
				if (promotionID != null && promotionID.intValue() > 0) {

					int M_PromotionPreCondition_ID = findPromotionPreConditionId(
							order, promotionCode, promotionID);
					if (M_PromotionPreCondition_ID > 0) {
						String update = "UPDATE M_PromotionPreCondition SET PromotionCounter = PromotionCounter - 1 WHERE M_PromotionPreCondition_ID = ?";
						DB.executeUpdate(update, M_PromotionPreCondition_ID, order.get_TrxName());
					}
				}
			}
		}
	}

	private int findPromotionPreConditionId(MOrder order, String promotionCode,
			Integer promotionID) {
		String bpFilter = "M_PromotionPreCondition.C_BPartner_ID = ? OR M_PromotionPreCondition.C_BP_Group_ID = ? OR (M_PromotionPreCondition.C_BPartner_ID IS NULL AND M_PromotionPreCondition.C_BP_Group_ID IS NULL)";
		String priceListFilter = "M_PromotionPreCondition.M_PriceList_ID IS NULL OR M_PromotionPreCondition.M_PriceList_ID = ?";
		String warehouseFilter = "M_PromotionPreCondition.M_Warehouse_ID IS NULL OR M_PromotionPreCondition.M_Warehouse_ID = ?";
		String dateFilter = "M_PromotionPreCondition.StartDate <= ? AND (M_PromotionPreCondition.EndDate >= ? OR M_PromotionPreCondition.EndDate IS NULL)";

		StringBuilder select = new StringBuilder();
		select.append(" SELECT M_PromotionPreCondition.M_PromotionPreCondition_ID FROM M_PromotionPreCondition ")
			.append(" WHERE")
			.append(" (" + bpFilter + ")")
			.append(" AND (").append(priceListFilter).append(")")
			.append(" AND (").append(warehouseFilter).append(")")
			.append(" AND (").append(dateFilter).append(")")
			.append(" AND (M_PromotionPreCondition.M_Promotion_ID = ?)")
			.append(" AND (M_PromotionPreCondition.IsActive = 'Y')");
		if (promotionCode != null && promotionCode.trim().length() > 0) {
			select.append(" AND (M_PromotionPreCondition.PromotionCode = ?)");
		} else {
			select.append(" AND (M_PromotionPreCondition.PromotionCode IS NULL)");
		}
		select.append(" ORDER BY M_PromotionPreCondition.C_BPartner_ID Desc, M_PromotionPreCondition.C_BP_Group_ID Desc, M_PromotionPreCondition.M_PriceList_ID Desc, M_PromotionPreCondition.M_Warehouse_ID Desc, M_PromotionPreCondition.StartDate Desc");
		int M_PromotionPreCondition_ID = 0;
		int C_BP_Group_ID = 0;
		try {
			C_BP_Group_ID = order.getC_BPartner().getC_BP_Group_ID();
		} catch (Exception e) {
		}
		if (promotionCode != null && promotionCode.trim().length() > 0) {
			M_PromotionPreCondition_ID = DB.getSQLValue(order.get_TrxName(), select.toString(), order.getC_BPartner_ID(),
					C_BP_Group_ID, order.getM_PriceList_ID(), order.getM_Warehouse_ID(), order.getDateOrdered(),
					order.getDateOrdered(), promotionID, promotionCode);
		} else {
			M_PromotionPreCondition_ID = DB.getSQLValue(order.get_TrxName(), select.toString(), order.getC_BPartner_ID(),
					C_BP_Group_ID, order.getM_PriceList_ID(), order.getM_Warehouse_ID(), order.getDateOrdered(),
					order.getDateOrdered(), promotionID);
		}
		return M_PromotionPreCondition_ID;
	}

}
