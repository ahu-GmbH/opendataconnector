package de.ahu.opendata.DataUtils;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;

@SuppressWarnings({ "serial" })
public class DataEvents {
	public static class DataEvent<T extends RestDTO> extends ComponentEvent<Component> {
		private final List<T> dtoEntity;
		private final String message;

		public DataEvent(Component source, boolean fromClient, List<T> dtoEntity, String message) {
			super(source, fromClient);
			this.dtoEntity = dtoEntity;
			this.message = message;
		}

		public List<T> getDtoEntity() {
			return dtoEntity;
		}

		public String getMessage() {
			return message;
		}
	}

	public static class DataSaveEvent<T extends RestDTO> extends DataEvent<T> {
		public DataSaveEvent(Component source, boolean fromClient, List<T> dtoEntity, String message) {
			super(source, fromClient, dtoEntity, message);
		}
	}

	public static class DataErrorEvent<T extends RestDTO> extends DataEvent<T> {
		public DataErrorEvent(Component source, boolean fromClient, List<T> dtoEntity, String errorMessage) {
			super(source, fromClient, dtoEntity, errorMessage);
		}
	}
}
