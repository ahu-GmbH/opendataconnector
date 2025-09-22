package de.ahu.opendata.Utils;

public interface SessionBeanAware {
	default SessionBean getSessionBean() {
		return SpringApplicationContext.getBean(SessionBean.class);
	}
}
