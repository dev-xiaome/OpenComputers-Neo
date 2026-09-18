
package li.cil.oc.data;

import li.cil.oc.OpenComputersNeo;
import li.cil.oc.common.init.OCBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

class OCBlockTagsProvider extends BlockTagsProvider {
    public OCBlockTagsProvider(
        PackOutput output,
        CompletableFuture<HolderLookup.Provider> lookupProvider,
        ExistingFileHelper existingFiles
    ) {
        super(output, lookupProvider, OpenComputersNeo.ID(), existingFiles);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {

        tag(BlockTags.BEACON_BASE_BLOCKS);

        tag(Tags.Blocks.END_STONES).add(OCBlocks.Endstone().get());
    }
}
