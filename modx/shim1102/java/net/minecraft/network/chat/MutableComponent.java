package net.minecraft.network.chat;

/** 1.12.2 垫片：1.19+ 的 {@code MutableComponent} 在 1.12 没有，退化成 {@link Component} 即可。 */
public class MutableComponent extends Component {

    public MutableComponent(net.minecraft.util.text.ITextComponent handle) {
        super(handle);
    }
}
