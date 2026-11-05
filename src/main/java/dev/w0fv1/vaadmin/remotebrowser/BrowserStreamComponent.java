package dev.w0fv1.vaadmin.remotebrowser;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

@Tag("browser-stream")
@JsModule("./browser-stream.js")
public final class BrowserStreamComponent extends Component {
    public BrowserStreamComponent() {
        getElement().getStyle().set("width", "100%").set("display", "block");
        setStreamPath(BrowserRuntimeProperties.DEFAULT_WEBSOCKET_PATH);
    }

    public void setStreamPath(String streamPath) {
        getElement().setProperty("streamPath", streamPath);
    }

    public void setOnlyPage(boolean onlyPage) {
        getElement().setProperty("onlyPage", onlyPage);
    }

    public void setSessionId(String sessionId) {
        getElement().setProperty("sessionId", sessionId);
    }
}
