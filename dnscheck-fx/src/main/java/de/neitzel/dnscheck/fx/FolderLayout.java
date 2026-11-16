package de.neitzel.dnscheck.fx;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import de.neitzel.fx.docking.DockTarget;
import de.neitzel.fx.docking.DockableId;
import de.neitzel.fx.docking.LayoutSnapshot;
import de.neitzel.fx.docking.TabPosition;
import javafx.geometry.Rectangle2D;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** The persisted docking layout for one working folder. */
public final class FolderLayout {
    static final String FILENAME = ".dnscheck-layout.json";
    private static final ObjectMapper JSON = mapper();

    public record Saved(LayoutSnapshot layout, double sidebarWidth, Rectangle2D mainWindow) {}

    public Optional<Saved> read(Path directory) throws IOException {
        Path file = directory.resolve(FILENAME);
        if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        return Optional.ofNullable(JSON.readValue(file.toFile(), Saved.class));
    }

    public void write(Path directory, Saved saved) throws IOException {
        Path target = directory.resolve(FILENAME);
        Path temp = Files.createTempFile(directory, ".dnscheck-layout-", ".tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), saved);
            AtomicFiles.replace(temp, target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static ObjectMapper mapper() {
        var ids = new SimpleModule();
        ids.addKeyDeserializer(DockableId.class, new KeyDeserializer() {
            @Override public DockableId deserializeKey(String key, DeserializationContext context) {
                return new DockableId(key);
            }
        });
        ids.addKeySerializer(DockableId.class, new com.fasterxml.jackson.databind.JsonSerializer<>() {
            @Override public void serialize(DockableId value, com.fasterxml.jackson.core.JsonGenerator out,
                                            com.fasterxml.jackson.databind.SerializerProvider provider) throws IOException {
                out.writeFieldName(value.value());
            }
        });
        return new ObjectMapper().registerModule(ids)
                .addMixIn(LayoutSnapshot.Branch.class, BranchType.class)
                .addMixIn(DockTarget.class, TargetType.class)
                .addMixIn(TabPosition.class, PositionType.class)
                .addMixIn(Rectangle2D.class, Bounds.class);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = LayoutSnapshot.Group.class, name = "group"),
            @JsonSubTypes.Type(value = LayoutSnapshot.Split.class, name = "split")
    })
    private interface BranchType {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = DockTarget.Area.class, name = "area"),
            @JsonSubTypes.Type(value = DockTarget.Group.class, name = "group"),
            @JsonSubTypes.Type(value = DockTarget.Split.class, name = "split"),
            @JsonSubTypes.Type(value = DockTarget.Floating.class, name = "floating"),
            @JsonSubTypes.Type(value = DockTarget.ReturnToMain.class, name = "return")
    })
    private interface TargetType {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TabPosition.Append.class, name = "append"),
            @JsonSubTypes.Type(value = TabPosition.Before.class, name = "before"),
            @JsonSubTypes.Type(value = TabPosition.After.class, name = "after")
    })
    private interface PositionType {}

    @JsonIgnoreProperties({"maxX", "maxY"})
    private abstract static class Bounds {
        @JsonCreator Bounds(@JsonProperty("minX") double x, @JsonProperty("minY") double y,
                            @JsonProperty("width") double width, @JsonProperty("height") double height) {}
    }
}
