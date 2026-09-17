# OpenComputers マニュアル

OpenComputersは、永続的でモジュール式、かつ高度な構成が可能な[コンピューター](general/computer.md)、[サーバー](item/server1.md)、[ロボット](block/robot.md)、および[ドローン](item/drone.md)をゲームに追加するMODです。すべてのデバイスは Lua 5.2 を使用してプログラムでき、用途に応じて様々な複雑さのシステムを構築できます。

マニュアルの使い方については、[マニュアルについてのページ](item/manual.md)を参照してください（緑色のテキストはリンクになっており、クリックできます）。

## 目次

### デバイス
- [コンピューター](general/computer.md)
- [サーバー](item/server1.md)
- [マイクロコントローラー](block/microcontroller.md)
- [ロボット](block/robot.md)
- [ドローン](item/drone.md)

### ソフトウェアとプログラミング
- [OpenOS](general/openos.md)
- [Lua](general/lua.md)

### ブロックとアイテム
- [アイテム](item/index.md)
- [ブロック](block/index.md)

### ガイド
- [はじめに (導入ガイド)](general/quickstart.md)

## 概要

上述の通り、OpenComputersのコンピューターは「永続性」を備えています。これは、実行中の[コンピューター](general/computer.md)が設置されているチャンクがアンロードされても、その状態が保持されることを意味します。つまり、プレイヤーが[コンピューター](general/computer.md)から離れたり、ログオフしたりしても、[コンピューター](general/computer.md)は最後に確認された状態を記憶しています。そしてプレイヤーが再び[コンピューター](general/computer.md)に近づいたときに、その時点から動作を継続します。永続性は、[タブレット](item/tablet.md)を除くすべてのデバイスで機能します。

すべてのデバイスはモジュール式であり、現実世界のコンピューターと同じように幅広いコンポーネントを組み合わせて構築できます。工作好きなプレイヤーであれば、心ゆくまでデバイスを最適化できるでしょう。必要であれば、構成が気に入らなかった場合にデバイスを[分解](block/disassembler.md)して作り直すことも可能です。[コンピューター](general/computer.md)や[サーバー](item/server1.md)の場合、対応するGUIを開くだけで、コンポーネントをその場で交換できます。

OpenComputersのデバイスは、ブロックやエンティティを操作するための多くの異なるMODと互換性があります（[アダプター](block/adapter.md)や、[ロボット](block/robot.md)または[ドローン](item/drone.md)の特定のアップグレードを介して行います）。電力は、Redstone Flux (RF)、IndustrialCraft2 EU、Mekanism Joules、Applied Energistics 2、Factorization Chargeなどを含む（これらに限定されません）、幅広い他の電力MODから供給できます。

OpenComputersのデバイスには追加の機能がある一方で、いくつかの制限もあります。[コンピューター](general/computer.md)は基本となるデバイスで、使用するCPUのティアに応じてかなりの数のコンポーネントを搭載できます。また、[コンピューター](general/computer.md)は全6面からコンポーネントにアクセスできます。[サーバー](item/server1.md)は、[コンポーネントバス](item/componentBus1.md)を使用することで、[コンピューター](general/computer.md)よりも多くのコンポーネント（内部または外部）に接続できます。ただし、[ラック](block/rack.md)を使用するため、[サーバー](item/server1.md)は構成に応じて[ラック](block/rack.md)の1つの面からしかコンポーネントにアクセスできません。[マイクロコントローラー](block/microcontroller.md)は、[ハードドライブ](item/hdd1.md)や[ディスクドライブ](block/diskDrive.md)のスロットがないため、[コンピューター](general/computer.md)よりもさらに制限されています。これは、[マイクロコントローラー](block/microcontroller.md)に [OpenOS](general/openos.md) をインストールできないことを意味します。ただし、[マイクロコントローラー](block/microcontroller.md)には [EEPROM](item/eeprom.md) スロットがあり、特定のタスクに特化したOSを書き込んで使用できます。

[ロボット](block/robot.md)は移動可能な[コンピューター](general/computer.md)であり、プレイヤーと同じように世界とやり取りできます（ただし、外部のOpenComputersブロックとはやり取りできません）。[コンピューター](general/computer.md)とは異なり、一度組み立てられたロボット内部のコンポーネントを取り外すことはできません。この制限を回避するために、[ロボット](block/robot.md)には[アップグレードコンテナ](item/upgradeContainer1.md)や[カードコンテナ](item/cardContainer1.md)を組み込むことができ、必要に応じてカードやアップグレードをその場で交換できるようになります。[ロボット](block/robot.md)に [OpenOS](general/openos.md) をインストールするには、コンテナスロットに[ディスクドライブ](block/diskDrive.md)を配置して[フロッピー](item/floppy.md)を挿入できるようにするか、[OpenOS](general/openos.md) がインストール済みの[ハードドライブ](item/hdd1.md)をスロットに配置します。[ロボット](block/robot.md)を完全に再構成するには、まず[分解](block/disassembler.md)する必要があります。[ドローン](item/drone.md)は[ロボット](block/robot.md)の機能限定版です。移動方法が異なり、インベントリスロットが少なく、OSも搭載していません（[マイクロコントローラー](block/microcontroller.md)と同様に、[ドローン](item/drone.md)には特定のタスク用にプログラムされた [EEPROM](item/eeprom.md) を構成できます）。ほとんどの場合、[ロボット](block/robot.md)と[ドローン](item/drone.md)は同じアップグレードとコンポーネントを共有しますが、[ドローン](item/drone.md)では[インベントリアップグレード](item/inventoryUpgrade.md)によるスロット付与数が少ない（1つにつき4スロット、最大8スロット）のに対し、[ロボット](block/robot.md)はより多くの[インベントリアップグレード](item/inventoryUpgrade.md)（合計4つまで）を搭載でき、1つあたりのスロット数も多い（16スロット）といった挙動の違いがあります。

このマニュアルには、すべてのブロックとアイテムに関する詳細情報のほか、様々なタイプのシステムやデバイスのセットアップ方法、そして Lua プログラミングの入門編が含まれています。
