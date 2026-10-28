package net.minecraft.network.chat;

/** 1.7.10 垫片：1.19+ 的 {@code MutableComponent} 在这儿不存在，退化成 {@link Component} 即可。 */
public class MutableComponent extends Component {

    public MutableComponent(net.minecraft.util.IChatComponent handle) {
        super(handle);
    }
}
